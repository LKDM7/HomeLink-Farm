package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import fr.lkdm.homelink.farm.farm.bot.FarmBotFault;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.item.FarmBotItem;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import fr.lkdm.homelink.farm.registry.ModEntities;
import fr.lkdm.homelink.farm.registry.ModItems;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * FarmBot and FarmBot Station, end to end in a real world: installation, navigation through
 * crop rows, harvest and replanting through the CropAdapters, battery, docking, unloading,
 * commands, failure paths and item conservation. Each test builds its own small farm on a dirt
 * floor (y = 0): crops stand at y = 1, the station faces north at (8, 1, 15) and docks the
 * robot on (8, 1, 14).
 */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FarmBotGameTests {
    static final BlockPos STATION = new BlockPos(8, 1, 15);
    static final BlockPos DOCK = new BlockPos(8, 1, 14);
    static final BlockPos CONTROLLER = new BlockPos(0, 1, 16);
    static final BlockPos MONITOR = new BlockPos(1, 1, 16);

    private FarmBotGameTests() {
    }

    record Rig(FarmControllerBlockEntity controller, CropMonitorBlockEntity monitor, FarmBotStationBlockEntity station, FarmBotEntity bot) {
    }

    static BlockState crop(Block block, int age) {
        return ((CropBlock) block).getStateForAge(age);
    }

    static BlockState mature(Block block) {
        return crop(block, ((CropBlock) block).getMaxAge());
    }

    /** Dirt floor, farm controller, crop monitor watching x 2..14 / z 2..12, station and a docked robot. */
    static Rig rig(GameTestHelper helper) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
        }
        helper.setBlock(CONTROLLER, ModBlocks.FARM_CONTROLLER.get());
        helper.setBlock(MONITOR, ModBlocks.CROP_MONITOR.get());
        helper.setBlock(STATION, ModBlocks.FARMBOT_STATION.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.NORTH));
        FarmControllerBlockEntity controller = helper.getBlockEntity(CONTROLLER);
        CropMonitorBlockEntity monitor = helper.getBlockEntity(MONITOR);
        FarmBotStationBlockEntity station = helper.getBlockEntity(STATION);
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(14, 1, 12))));
        FarmLinkService.link(helper.getLevel(), controller, monitor, 32);
        FarmLinkService.link(helper.getLevel(), controller, station, 32);
        helper.assertTrue(station.wholeFarm() && station.farmMonitors() == 1, "Station linked to a controller does not work on the whole farm");
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var result = station.install(helper.getLevel(), player, FarmBotItem.create(100, 0, null));
        helper.assertTrue(result.success(), "Install failed: " + result);
        return new Rig(controller, monitor, station, robot(helper, station));
    }

    static FarmBotEntity robot(GameTestHelper helper, FarmBotStationBlockEntity station) {
        var entity = helper.getLevel().getEntity(station.robotId().orElseThrow());
        helper.assertTrue(entity instanceof FarmBotEntity, "Robot entity missing");
        return (FarmBotEntity) entity;
    }

    static void plant(GameTestHelper helper, BlockPos pos, BlockState crop) {
        helper.setBlock(pos.below(), Blocks.FARMLAND);
        helper.setBlock(pos, crop);
    }

    static int count(FarmBotStationBlockEntity station, FarmBotEntity bot, Item item) {
        int total = 0;
        for (var handler : List.of(station.output(), bot.inventory())) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (handler.getStackInSlot(slot).is(item)) total += handler.getStackInSlot(slot).getCount();
            }
        }
        return total;
    }

    /** On its dock in front of the (north-facing) station, its back to the station. */
    static boolean docked(FarmBotEntity bot) {
        return bot.docked() && bot.blockPosition().equals(bot.stationPos().relative(Direction.NORTH))
                && Math.abs(net.minecraft.util.Mth.wrapDegrees(bot.getYRot() - Direction.NORTH.toYRot())) < 1.0F;
    }

    // ----- Installation ---------------------------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 100)
    public static void robotInstallsDockedAndBelongsToOneStation(GameTestHelper helper) {
        Rig rig = rig(helper);
        helper.assertTrue(docked(rig.bot()), "Robot not docked, back to the station, after install: yaw " + rig.bot().getYRot());
        helper.assertTrue(rig.bot().blockPosition().equals(helper.absolutePos(DOCK)), "Robot not on the dock: " + rig.bot().blockPosition());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var again = rig.station().install(helper.getLevel(), player, FarmBotItem.create(100, 0, null));
        helper.assertTrue(again == FarmBotStationBlockEntity.InstallResult.OCCUPIED, "Second robot accepted: " + again);
        // A second station never claims the first robot.
        BlockPos other = new BlockPos(12, 1, 15);
        helper.setBlock(other, ModBlocks.FARMBOT_STATION.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.NORTH));
        FarmBotStationBlockEntity second = helper.getBlockEntity(other);
        helper.assertFalse(second.owns(rig.bot().getUUID()), "Another station owns the robot");
        helper.assertTrue(rig.bot().home(helper.getLevel()).map(home -> home == rig.station()).orElse(false), "Robot lost its station");
        // A blocked dock refuses the installation.
        helper.setBlock(new BlockPos(12, 1, 14), Blocks.STONE);
        var blocked = second.install(helper.getLevel(), player, FarmBotItem.create(100, 0, null));
        helper.assertTrue(blocked == FarmBotStationBlockEntity.InstallResult.DOCK_BLOCKED, "Blocked dock accepted: " + blocked);
        helper.succeed();
    }

    // ----- Crop Monitor choice: the Farm Controller is optional ------------------------------

    /** Floor, a monitor watching x 2..14 / z 2..12 and a station, without any Farm Controller. */
    static FarmBotStationBlockEntity standaloneStation(GameTestHelper helper, java.util.UUID monitorOwner, java.util.UUID stationOwner) {
        for (int x = 0; x <= 16; x++) {
            for (int z = 0; z <= 16; z++) helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
        }
        helper.setBlock(MONITOR, ModBlocks.CROP_MONITOR.get());
        CropMonitorBlockEntity monitor = helper.getBlockEntity(MONITOR);
        monitor.setOwner(monitorOwner, "monitor_owner");
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(14, 1, 12))));
        helper.setBlock(STATION, ModBlocks.FARMBOT_STATION.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.NORTH));
        FarmBotStationBlockEntity station = helper.getBlockEntity(STATION);
        station.setOwner(stationOwner, "station_owner");
        return station;
    }

    @GameTest(template = "field", timeoutTicks = 1200)
    public static void stationWithoutControllerUsesTheNearbyMonitor(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        FarmBotStationBlockEntity station = standaloneStation(helper, player.getUUID(), player.getUUID());
        helper.assertTrue(station.install(helper.getLevel(), player, FarmBotItem.create(100, 0, null)).success(), "Install failed");
        FarmBotEntity bot = robot(helper, station);
        BlockPos crop = new BlockPos(6, 1, 6);
        plant(helper, crop, mature(Blocks.CARROTS));
        CropMonitorBlockEntity monitor = helper.getBlockEntity(MONITOR);
        monitor.scanner().requestPass();
        helper.succeedWhen(() -> {
            helper.assertTrue(station.controllerLink().isEmpty(), "Station unexpectedly linked to a controller");
            helper.assertTrue(station.monitorId().map(id -> id.equals(monitor.componentId())).orElse(false), "Nearby monitor not picked");
            helper.assertTrue(bot.harvested() == 1 && docked(bot), "Robot did not harvest from the nearby monitor");
        });
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void anotherPlayersMonitorIsNeverUsed(GameTestHelper helper) {
        FarmBotStationBlockEntity station = standaloneStation(helper, java.util.UUID.randomUUID(), java.util.UUID.randomUUID());
        CropMonitorBlockEntity foreign = helper.getBlockEntity(MONITOR);
        station.cycleMonitor(helper.getLevel(), null);
        // Other tests' monitors may sit within range in this shared world: only this foreign one matters.
        helper.runAfterDelay(100, () -> {
            helper.assertFalse(station.monitorId().map(id -> id.equals(foreign.componentId())).orElse(false),
                    "Station picked a monitor owned by someone else");
            helper.assertFalse(station.cropSources(helper.getLevel()).contains(foreign), "Foreign monitor used as a crop source");
            helper.succeed();
        });
    }

    @GameTest(template = "field", timeoutTicks = 1600)
    public static void wholeFarmModeWorksOnEveryMonitor(GameTestHelper helper) {
        Rig rig = rig(helper);
        // A second monitor of the same farm, watching another strip.
        BlockPos secondPos = new BlockPos(2, 1, 16);
        helper.setBlock(secondPos, ModBlocks.CROP_MONITOR.get());
        CropMonitorBlockEntity second = helper.getBlockEntity(secondPos);
        rig.monitor().setZone(new CropZone(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(14, 1, 5))));
        second.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 1, 8)), helper.absolutePos(new BlockPos(14, 1, 11))));
        FarmLinkService.link(helper.getLevel(), rig.controller(), second, 32);
        plant(helper, new BlockPos(5, 1, 4), mature(Blocks.WHEAT));
        plant(helper, new BlockPos(9, 1, 10), mature(Blocks.POTATOES));
        rig.monitor().scanner().requestPass();
        second.scanner().requestPass();
        helper.succeedWhen(() -> {
            helper.assertTrue(rig.station().wholeFarm() && rig.station().farmMonitors() == 2, "Whole farm not counting both monitors");
            helper.assertTrue(rig.bot().harvested() == 2 && docked(rig.bot()), "Harvested " + rig.bot().harvested() + " of 2");
            // The MONITOR button pins one monitor, then comes back to the whole farm.
            rig.station().cycleMonitor(helper.getLevel(), null);
            helper.assertTrue(!rig.station().wholeFarm() && rig.station().monitorId().isPresent(), "Could not pin one monitor");
            rig.station().cycleMonitor(helper.getLevel(), null);
            rig.station().cycleMonitor(helper.getLevel(), null);
            helper.assertTrue(rig.station().wholeFarm(), "Cycle did not return to the whole farm");
        });
    }

    // ----- Harvest, replanting, unloading ---------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 1600)
    public static void harvestsAndReplantsTheFourCrops(GameTestHelper helper) {
        Rig rig = rig(helper);
        List<BlockPos> crops = List.of(new BlockPos(4, 1, 4), new BlockPos(6, 1, 4), new BlockPos(8, 1, 4), new BlockPos(10, 1, 4));
        List<Block> blocks = List.of(Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES, Blocks.BEETROOTS);
        for (int i = 0; i < crops.size(); i++) plant(helper, crops.get(i), mature(blocks.get(i)));
        // Young crops in the rows the robot drives through must survive.
        List<BlockPos> young = new ArrayList<>();
        for (int x = 3; x <= 12; x++) {
            BlockPos pos = new BlockPos(x, 1, 7);
            plant(helper, pos, crop(Blocks.WHEAT, 2));
            young.add(pos);
        }
        rig.monitor().scanner().requestPass();
        Set<FarmBotState> seen = EnumSet.noneOf(FarmBotState.class);
        double[] farthest = {0};
        helper.onEachTick(() -> {
            seen.add(rig.bot().brain().state());
            farthest[0] = Math.max(farthest[0], rig.bot().position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(DOCK))));
        });
        helper.succeedWhen(() -> {
            for (int i = 0; i < crops.size(); i++) {
                BlockState state = helper.getBlockState(crops.get(i));
                helper.assertTrue(state.is(blocks.get(i)) && ((CropBlock) blocks.get(i)).getAge(state) < ((CropBlock) blocks.get(i)).getMaxAge(),
                        "Crop " + i + " not harvested and replanted: " + state);
            }
            helper.assertTrue(docked(rig.bot()) && rig.bot().inventoryEmpty(), "Robot not back and unloaded: " + rig.bot().brain().state());
            helper.assertTrue(count(rig.station(), rig.bot(), Items.WHEAT) >= 1, "No wheat in the station");
            helper.assertTrue(count(rig.station(), rig.bot(), Items.CARROT) >= 1, "No carrot in the station");
            helper.assertTrue(count(rig.station(), rig.bot(), Items.POTATO) >= 1, "No potato in the station");
            helper.assertTrue(count(rig.station(), rig.bot(), Items.BEETROOT) >= 1, "No beetroot in the station");
            for (BlockPos pos : young) {
                helper.assertBlockPresent(Blocks.WHEAT, pos);
                helper.assertBlockPresent(Blocks.FARMLAND, pos.below());
            }
            helper.assertTrue(rig.bot().harvested() == 4, "Harvest counter " + rig.bot().harvested());
            helper.assertTrue(farthest[0] > 8, "Robot never drove to the crops (" + farthest[0] + " blocks)");
            helper.assertTrue(seen.containsAll(EnumSet.of(FarmBotState.MOVING, FarmBotState.HARVESTING, FarmBotState.RETURNING, FarmBotState.UNLOADING)),
                    "States seen: " + seen);
        });
    }

    @GameTest(template = "field", timeoutTicks = 1600)
    public static void fullRobotReturnsAndUnloads(GameTestHelper helper) {
        Rig rig = rig(helper);
        FarmBotEntity bot = rig.bot();
        rig.station().setWorking(false);
        for (int x = 4; x <= 8; x += 2) plant(helper, new BlockPos(x, 1, 5), mature(Blocks.WHEAT));
        rig.monitor().scanner().requestPass();
        List<Item> cargo = List.of(Items.STONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.COBBLESTONE, Items.OAK_LOG, Items.GLASS, Items.CLAY_BALL);
        Set<FarmBotState> seen = EnumSet.noneOf(FarmBotState.class);
        helper.onEachTick(() -> seen.add(bot.brain().state()));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.monitor().result().map(result -> result.harvestable().size() == 3).orElse(false),
                        "Monitor has not reported the crops yet"))
                // Out in the field with eight slots taken: the first harvest fills it.
                .thenExecute(() -> {
                    bot.setDocked(false);
                    bot.moveTo(helper.absoluteVec(new Vec3(6.5, 1, 7.5)));
                    for (int slot = 0; slot < cargo.size(); slot++) bot.inventory().setStackInSlot(slot, new ItemStack(cargo.get(slot), 10));
                    rig.station().setWorking(true);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(seen.contains(FarmBotState.STORAGE_FULL), "Never full: " + seen);
                    helper.assertTrue(docked(bot), "Robot not docked: " + bot.brain().state());
                    helper.assertTrue(bot.harvested() == 3, "Harvest counter " + bot.harvested());
                    for (Item item : cargo) helper.assertTrue(count(rig.station(), bot, item) == 10, "Lost or duplicated " + item);
                    // Each mature wheat yields exactly one wheat item: none lost, none duplicated.
                    helper.assertTrue(count(rig.station(), bot, Items.WHEAT) == 3, "Wheat " + count(rig.station(), bot, Items.WHEAT));
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 1200)
    public static void outputBlockedKeepsTheCargo(GameTestHelper helper) {
        Rig rig = rig(helper);
        for (int slot = 0; slot < FarmBotStationBlockEntity.OUTPUT_SIZE; slot++) rig.station().output().setStackInSlot(slot, new ItemStack(Items.DIRT, 64));
        rig.bot().inventory().setStackInSlot(0, new ItemStack(Items.WHEAT, 5));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().brain().state() == FarmBotState.OUTPUT_BLOCKED, "State " + rig.bot().brain().state()))
                .thenExecute(() -> helper.assertTrue(rig.bot().inventory().getStackInSlot(0).getCount() == 5, "Cargo changed while blocked"))
                .thenExecute(() -> rig.station().output().setStackInSlot(3, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    helper.assertTrue(rig.bot().inventoryEmpty(), "Unloading did not resume");
                    helper.assertTrue(rig.station().output().getStackInSlot(3).is(Items.WHEAT)
                            && rig.station().output().getStackInSlot(3).getCount() == 5, "Wheat not in the freed slot");
                })
                .thenSucceed();
    }

    // ----- Battery ---------------------------------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 1600)
    public static void lowBatteryDrivesHomeAndRecharges(GameTestHelper helper) {
        Rig rig = rig(helper);
        FarmBotEntity bot = rig.bot();
        plant(helper, new BlockPos(3, 1, 2), mature(Blocks.WHEAT));
        rig.monitor().scanner().requestPass();
        Set<FarmBotState> seen = EnumSet.noneOf(FarmBotState.class);
        helper.onEachTick(() -> seen.add(bot.brain().state()));
        helper.startSequence()
                // A few blocks out in the field, the battery drops to just above the threshold.
                .thenWaitUntil(() -> helper.assertTrue(!bot.docked()
                        && bot.position().distanceTo(Vec3.atBottomCenterOf(helper.absolutePos(DOCK))) > 3, "Robot still near its dock"))
                .thenExecute(() -> bot.setEnergy(FarmBotEntity.capacity() * 0.205))
                .thenWaitUntil(() -> helper.assertTrue(seen.contains(FarmBotState.LOW_BATTERY), "Never LOW_BATTERY: " + seen))
                .thenWaitUntil(() -> helper.assertTrue(docked(bot), "Not docked: " + bot.brain().state()))
                .thenWaitUntil(() -> helper.assertTrue(bot.brain().state() == FarmBotState.CHARGING || bot.batteryPercent() >= 80, "Not charging"))
                .thenWaitUntil(() -> helper.assertTrue(bot.batteryPercent() >= 80, "Battery " + bot.batteryPercent()))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 700)
    public static void dockedRobotChargesToFull(GameTestHelper helper) {
        Rig rig = rig(helper);
        rig.bot().setEnergy(FarmBotEntity.capacity() * 0.10);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().brain().state() == FarmBotState.CHARGING, "State " + rig.bot().brain().state()))
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().batteryPercent() == 100, "Battery " + rig.bot().batteryPercent()))
                .thenExecute(() -> helper.assertTrue(rig.bot().brain().state() == FarmBotState.IDLE, "Full robot state " + rig.bot().brain().state()))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 400)
    public static void emptyBatteryStopsInPlace(GameTestHelper helper) {
        Rig rig = rig(helper);
        FarmBotEntity bot = rig.bot();
        bot.setDocked(false);
        bot.moveTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        bot.setEnergy(0);
        plant(helper, new BlockPos(10, 1, 4), mature(Blocks.WHEAT));
        rig.monitor().scanner().requestPass();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(bot.brain().state() == FarmBotState.OUT_OF_POWER, "State " + bot.brain().state());
            helper.assertTrue(bot.position().distanceTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5))) < 0.2, "Empty robot moved");
            helper.assertFalse(bot.docked(), "Empty robot teleported home");
            helper.succeed();
        });
    }

    // ----- Commands -------------------------------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 1200)
    public static void pauseAndStartControlDeparture(GameTestHelper helper) {
        Rig rig = rig(helper);
        rig.station().setWorking(false);
        plant(helper, new BlockPos(5, 1, 5), mature(Blocks.CARROTS));
        rig.monitor().scanner().requestPass();
        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    helper.assertTrue(rig.bot().docked(), "Paused robot left");
                    helper.assertTrue(rig.bot().brain().state() == FarmBotState.PAUSED, "State " + rig.bot().brain().state());
                    rig.station().setWorking(true);
                })
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().harvested() == 1, "Robot did not work after START"))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 1200)
    public static void returnHomeCancelsTheTarget(GameTestHelper helper) {
        Rig rig = rig(helper);
        BlockPos far = new BlockPos(3, 1, 2);
        plant(helper, far, mature(Blocks.POTATOES));
        rig.monitor().scanner().requestPass();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().brain().state() == FarmBotState.MOVING, "Not moving"))
                .thenIdle(10)
                .thenExecute(() -> {
                    rig.station().setWorking(false);
                    rig.station().requestReturn();
                })
                .thenWaitUntil(() -> helper.assertTrue(docked(rig.bot()), "Not back: " + rig.bot().brain().state()))
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(far).is(Blocks.POTATOES)
                            && ((CropBlock) Blocks.POTATOES).isMaxAge(helper.getBlockState(far)), "Target harvested despite RETURN HOME");
                    helper.assertTrue(rig.bot().brain().target().isEmpty(), "Target kept");
                })
                .thenSucceed();
    }

    // ----- Failure paths --------------------------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 2400)
    public static void unreachableCropIsSkipped(GameTestHelper helper) {
        Rig rig = rig(helper);
        BlockPos walled = new BlockPos(4, 1, 4);
        plant(helper, walled, mature(Blocks.WHEAT));
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                helper.setBlock(walled.offset(dx, 0, dz), Blocks.STONE);
                helper.setBlock(walled.offset(dx, 1, dz), Blocks.STONE);
            }
        }
        BlockPos open = new BlockPos(10, 1, 6);
        plant(helper, open, mature(Blocks.WHEAT));
        rig.monitor().scanner().requestPass();
        Set<FarmBotFault> faults = EnumSet.noneOf(FarmBotFault.class);
        helper.onEachTick(() -> faults.add(rig.bot().brain().fault()));
        helper.succeedWhen(() -> {
            helper.assertTrue(((CropBlock) Blocks.WHEAT).getAge(helper.getBlockState(open)) == 0, "Reachable crop not harvested");
            helper.assertTrue(((CropBlock) Blocks.WHEAT).isMaxAge(helper.getBlockState(walled)), "Walled crop harvested");
            helper.assertTrue(faults.contains(FarmBotFault.TARGET_UNREACHABLE), "Unreachable crop never reported: " + faults);
            helper.assertTrue(docked(rig.bot()), "Robot did not come home: " + rig.bot().brain().state());
        });
    }

    @GameTest(template = "field", timeoutTicks = 1200)
    public static void blockedDockMakesTheRobotWait(GameTestHelper helper) {
        Rig rig = rig(helper);
        plant(helper, new BlockPos(4, 1, 6), mature(Blocks.BEETROOTS));
        rig.monitor().scanner().requestPass();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().harvested() == 1, "No harvest"))
                .thenExecute(() -> helper.setBlock(DOCK, Blocks.STONE))
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().brain().fault() == FarmBotFault.DOCK_BLOCKED, "Fault " + rig.bot().brain().fault()))
                .thenExecute(() -> helper.assertTrue(rig.bot().brain().state() == FarmBotState.STUCK, "State " + rig.bot().brain().state()))
                .thenExecute(() -> helper.setBlock(DOCK, Blocks.AIR))
                .thenWaitUntil(() -> helper.assertTrue(docked(rig.bot()), "Robot did not dock once freed"))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 1200)
    public static void unreachableStationIsReportedWithoutLooping(GameTestHelper helper) {
        Rig rig = rig(helper);
        FarmBotEntity bot = rig.bot();
        bot.setDocked(false);
        bot.moveTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        // Wall the robot in: it cannot reach its station.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                helper.setBlock(new BlockPos(4 + dx, 1, 4 + dz), Blocks.STONE);
                helper.setBlock(new BlockPos(4 + dx, 2, 4 + dz), Blocks.STONE);
            }
        }
        rig.station().requestReturn();
        helper.succeedWhen(() -> {
            helper.assertTrue(bot.brain().state() == FarmBotState.STUCK && bot.brain().fault() == FarmBotFault.HOME_UNREACHABLE,
                    "State " + bot.brain().state() + " / " + bot.brain().fault());
            helper.assertFalse(bot.docked(), "Robot teleported home");
        });
    }

    // ----- Players and destruction ----------------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 100)
    public static void pickUpGivesRobotAndCargoBack(GameTestHelper helper) {
        Rig rig = rig(helper);
        rig.bot().inventory().setStackInSlot(2, new ItemStack(Items.CARROT, 7));
        rig.bot().addHarvested(12);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.getInventory().clearContent();
        rig.bot().pickUp(player);
        helper.assertTrue(rig.bot().isRemoved(), "Robot still in the world");
        helper.assertFalse(rig.station().hasRobot(), "Station still claims the robot");
        helper.assertTrue(player.getInventory().countItem(Items.CARROT) == 7, "Cargo lost");
        ItemStack robot = player.getInventory().items.stream().filter(stack -> stack.is(ModItems.FARMBOT.get())).findFirst().orElse(ItemStack.EMPTY);
        helper.assertFalse(robot.isEmpty(), "Robot item missing");
        helper.assertTrue(FarmBotItem.data(robot).harvested() == 12, "Harvest counter lost");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 100)
    public static void killedRobotDropsItselfAndItsCargo(GameTestHelper helper) {
        Rig rig = rig(helper);
        rig.bot().inventory().setStackInSlot(0, new ItemStack(Items.POTATO, 9));
        rig.bot().kill();
        helper.assertTrue(rig.bot().isRemoved(), "Robot survived /kill");
        helper.assertFalse(rig.station().hasRobot(), "Station still claims the robot");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(DOCK)).inflate(2));
        int potatoes = drops.stream().filter(drop -> drop.getItem().is(Items.POTATO)).mapToInt(drop -> drop.getItem().getCount()).sum();
        long robots = drops.stream().filter(drop -> drop.getItem().is(ModItems.FARMBOT.get())).count();
        helper.assertTrue(potatoes == 9 && robots == 1, "Drops: " + potatoes + " potatoes, " + robots + " robots");
        // Ordinary damage never hurts it.
        helper.assertFalse(new FarmBotEntity(ModEntities.FARMBOT.get(), helper.getLevel())
                .hurt(helper.getLevel().damageSources().cactus(), 100), "Cactus damaged a robot");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void brokenStationDropsItsOutputAndOrphansTheRobot(GameTestHelper helper) {
        Rig rig = rig(helper);
        rig.station().output().setStackInSlot(4, new ItemStack(Items.WHEAT, 20));
        helper.getLevel().destroyBlock(helper.absolutePos(STATION), true);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().brain().state() == FarmBotState.ERROR
                        && rig.bot().brain().fault() == FarmBotFault.NO_STATION, "State " + rig.bot().brain().state()))
                .thenExecute(() -> {
                    var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(STATION)).inflate(2));
                    int wheat = drops.stream().filter(drop -> drop.getItem().is(Items.WHEAT)).mapToInt(drop -> drop.getItem().getCount()).sum();
                    helper.assertTrue(wheat == 20, "Output lost: " + wheat);
                })
                .thenSucceed();
    }

    // ----- HomeCore -------------------------------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 300)
    public static void stationIsAHomeCoreDeviceWithAuthorizedCommands(GameTestHelper helper) {
        Rig rig = rig(helper);
        var owner = HomeCoreGameTests.player(helper, "robot_owner");
        var viewer = HomeCoreGameTests.player(helper, "robot_viewer");
        rig.station().setOwner(owner.getUUID(), "robot_owner");
        var networks = fr.lkdm.homecore.api.DashboardAPI.networks(helper.getLevel().getServer());
        var network = networks.createNetwork("FarmBot test", owner.getUUID());
        networks.setMember(network.id(), viewer.getUUID(), fr.lkdm.homecore.api.network.NetworkRole.VIEWER);
        helper.succeedWhen(() -> {
            var device = rig.station().homeCoreDevice().orElse(null);
            helper.assertTrue(device != null, "Station not registered in HomeCore");
            helper.assertTrue(HomeCoreGameTests.metric(device, fr.lkdm.homelink.farm.homelink.FarmIds.FARMBOT_INSTALLED).equals(true), "Robot not reported");
            var battery = (fr.lkdm.homecore.api.metric.Percentage) HomeCoreGameTests.metric(device, fr.lkdm.homelink.farm.homelink.FarmIds.FARMBOT_BATTERY);
            helper.assertTrue(battery.value() == 100, "Battery metric " + battery);
            if (rig.station().homeNetwork().isEmpty()) {
                var bound = fr.lkdm.homelink.farm.homelink.HomeNetworkBinding.bind(owner, rig.station(), java.util.Optional.of(network.id()));
                helper.assertTrue(bound == fr.lkdm.homelink.farm.homelink.HomeNetworkBinding.Result.BOUND, "Bind " + bound);
            }
            var unit = fr.lkdm.homecore.api.action.Unit.INSTANCE;
            var denied = fr.lkdm.homecore.api.DashboardAPI.executeAction(viewer, network.id(), rig.station().deviceId(),
                    fr.lkdm.homelink.farm.homelink.FarmIds.ACTION_PAUSE, unit);
            helper.assertTrue(denied.code() == fr.lkdm.homecore.api.action.ActionResult.Code.DENIED, "VIEWER paused the robot: " + denied.code());
            helper.assertTrue(rig.station().working(), "Denied action changed the station");
            var paused = fr.lkdm.homecore.api.DashboardAPI.executeAction(owner, network.id(), rig.station().deviceId(),
                    fr.lkdm.homelink.farm.homelink.FarmIds.ACTION_PAUSE, unit);
            helper.assertTrue(paused.isSuccess() && !rig.station().working(), "PAUSE through HomeCore failed: " + paused.code());
            var started = fr.lkdm.homecore.api.DashboardAPI.executeAction(owner, network.id(), rig.station().deviceId(),
                    fr.lkdm.homelink.farm.homelink.FarmIds.ACTION_START, unit);
            helper.assertTrue(started.isSuccess() && rig.station().working(), "START through HomeCore failed: " + started.code());
            networks.deleteNetwork(network.id());
        });
    }

    @GameTest(template = "field", timeoutTicks = 1600)
    public static void farmBotEventsAreTransitionsNotSpam(GameTestHelper helper) {
        Rig rig = rig(helper);
        for (int x = 5; x <= 7; x += 2) plant(helper, new BlockPos(x, 1, 6), mature(Blocks.WHEAT));
        rig.monitor().scanner().requestPass();
        List<fr.lkdm.homecore.api.event.DeviceEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        var subscription = fr.lkdm.homecore.api.DashboardAPI.events(helper.getLevel().getServer()).subscribe(event -> {
            if (event.source().equals(rig.station().deviceId())) events.add(event);
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rig.bot().harvested() == 2 && docked(rig.bot()), "Harvest not finished"))
                .thenIdle(40)
                .thenExecute(() -> {
                    subscription.close();
                    long returned = events.stream().filter(event -> event.type().equals(fr.lkdm.homelink.farm.homelink.FarmIds.FARMBOT_RETURNED)).count();
                    var complete = events.stream().filter(event -> event.type().equals(fr.lkdm.homelink.farm.homelink.FarmIds.FARMBOT_HARVEST_COMPLETE)).toList();
                    helper.assertTrue(returned == 1, "farmbot_returned published " + returned + " times: " + events);
                    helper.assertTrue(complete.size() == 1 && "2".equals(complete.getFirst().data().get("crops")), "harvest_complete: " + complete);
                    helper.assertTrue(events.size() == 2, "Unexpected events: " + events);
                })
                .thenSucceed();
    }

    // ----- Performance ----------------------------------------------------------------------

    @GameTest(template = "large", timeoutTicks = 400)
    public static void targetSearchStaysCheapOnABigField(GameTestHelper helper) {
        // 32 x 20 mature wheat: more than the 512 positions a monitor keeps for the robots.
        for (int x = 1; x <= 32; x++) {
            for (int z = 1; z <= 20; z++) plant(helper, new BlockPos(x, 1, z), mature(Blocks.WHEAT));
        }
        helper.setBlock(new BlockPos(0, 1, 0), ModBlocks.CROP_MONITOR.get());
        CropMonitorBlockEntity monitor = helper.getBlockEntity(new BlockPos(0, 1, 0));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(1, 1, 1)), helper.absolutePos(new BlockPos(32, 1, 20))));
        Vec3 robot = helper.absoluteVec(new Vec3(16.5, 1, 24.5));
        helper.succeedWhen(() -> {
            helper.assertTrue(monitor.result().isPresent(), "Monitor not scanned yet");
            int listed = monitor.result().get().harvestable().size();
            helper.assertTrue(listed == fr.lkdm.homelink.farm.farm.crop.CropScanResult.MAX_HARVESTABLE, "Listed " + listed);
            long start = System.nanoTime();
            BlockPos target = null;
            for (int i = 0; i < 100; i++) {
                target = fr.lkdm.homelink.farm.farm.bot.FarmBotTargets.nearest(helper.getLevel(), monitor, robot, pos -> false);
            }
            double averageMs = (System.nanoTime() - start) / 1e6 / 100;
            fr.lkdm.homelink.farm.HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PERF farmbot_target_search candidates={} avg_ms={}", listed, averageMs);
            helper.assertTrue(target != null, "No target found");
            // A search happens at most once per farmbotSearchCooldown (2 s) per robot: well under a millisecond.
            helper.assertTrue(averageMs < 1.0, "Target search took " + averageMs + " ms");
        });
    }

    // ----- CropAdapter harvest strategies ---------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 100)
    public static void cropAdaptersDescribeEachHarvest(GameTestHelper helper) {
        var level = helper.getLevel();
        var empty = new net.neoforged.neoforge.items.ItemStackHandler(9);
        // Nether wart: broken, replanted at age 0 with one of its own drops.
        BlockPos wart = new BlockPos(2, 2, 2);
        helper.setBlock(wart.below(), Blocks.SOUL_SAND);
        helper.setBlock(wart, Blocks.NETHER_WART.defaultBlockState().setValue(net.minecraft.world.level.block.NetherWartBlock.AGE, 3));
        var wartDrops = fr.lkdm.homelink.farm.farm.bot.CropHarvester.harvest(level, helper.absolutePos(wart), null, empty).orElseThrow();
        helper.assertTrue(helper.getBlockState(wart).is(Blocks.NETHER_WART)
                && helper.getBlockState(wart).getValue(net.minecraft.world.level.block.NetherWartBlock.AGE) == 0, "Nether wart not replanted");
        helper.assertTrue(wartDrops.stream().allMatch(stack -> stack.is(Items.NETHER_WART)), "Nether wart drops " + wartDrops);
        // Cocoa: replanted on the same log face.
        BlockPos log = new BlockPos(5, 2, 2);
        helper.setBlock(log, Blocks.JUNGLE_LOG);
        BlockState cocoa = Blocks.COCOA.defaultBlockState().setValue(net.minecraft.world.level.block.CocoaBlock.AGE, 2)
                .setValue(net.minecraft.world.level.block.CocoaBlock.FACING, Direction.SOUTH);
        helper.setBlock(log.north(), cocoa);
        fr.lkdm.homelink.farm.farm.bot.CropHarvester.harvest(level, helper.absolutePos(log.north()), null, empty).orElseThrow();
        BlockState replanted = helper.getBlockState(log.north());
        helper.assertTrue(replanted.is(Blocks.COCOA) && replanted.getValue(net.minecraft.world.level.block.CocoaBlock.AGE) == 0
                && replanted.getValue(net.minecraft.world.level.block.CocoaBlock.FACING) == Direction.SOUTH, "Cocoa not replanted: " + replanted);
        // Sweet berries: picked, the bush stays.
        BlockPos bush = new BlockPos(8, 2, 2);
        helper.setBlock(bush.below(), Blocks.GRASS_BLOCK);
        helper.setBlock(bush, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE, 3));
        var berries = fr.lkdm.homelink.farm.farm.bot.CropHarvester.harvest(level, helper.absolutePos(bush), null, empty).orElseThrow();
        helper.assertTrue(helper.getBlockState(bush).getValue(net.minecraft.world.level.block.SweetBerryBushBlock.AGE) == 1, "Bush not reset");
        int picked = berries.stream().filter(stack -> stack.is(Items.SWEET_BERRIES)).mapToInt(ItemStack::getCount).sum();
        helper.assertTrue(picked >= 2 && picked <= 3, "Picked " + picked + " berries");
        // Wheat without any seed in the drops or the robot: harvested, the spot stays empty (nothing is created).
        BlockPos wheat = new BlockPos(11, 2, 2);
        plant(helper, wheat, mature(Blocks.WHEAT));
        var seedless = new net.neoforged.neoforge.items.ItemStackHandler(9);
        var wheatDrops = fr.lkdm.homelink.farm.farm.bot.CropHarvester.harvest(level, helper.absolutePos(wheat), null, seedless).orElseThrow();
        long seeds = wheatDrops.stream().filter(stack -> stack.is(Items.WHEAT_SEEDS)).mapToInt(ItemStack::getCount).sum();
        boolean replantedWheat = helper.getBlockState(wheat).is(Blocks.WHEAT);
        helper.assertTrue(replantedWheat || seeds == 0, "Replanted without consuming a seed");
        // Young crops and stems are never harvested.
        BlockPos young = new BlockPos(13, 2, 2);
        plant(helper, young, crop(Blocks.CARROTS, 3));
        helper.assertTrue(fr.lkdm.homelink.farm.farm.bot.CropHarvester.harvest(level, helper.absolutePos(young), null, empty).isEmpty(), "Young carrot harvested");
        BlockPos stem = new BlockPos(15, 2, 2);
        plant(helper, stem, Blocks.MELON_STEM.defaultBlockState().setValue(net.minecraft.world.level.block.StemBlock.AGE, 7));
        helper.assertTrue(fr.lkdm.homelink.farm.farm.bot.CropHarvester.harvest(level, helper.absolutePos(stem), null, empty).isEmpty(), "Stem harvested");
        helper.succeed();
    }

    // ----- Persistence and multiple robots ---------------------------------------------------

    @GameTest(template = "field", timeoutTicks = 100)
    public static void robotAndStationSurviveSaveAndLoad(GameTestHelper helper) {
        Rig rig = rig(helper);
        rig.bot().inventory().setStackInSlot(1, new ItemStack(Items.BEETROOT, 13));
        rig.bot().setEnergy(FarmBotEntity.capacity() * 0.42);
        rig.bot().addHarvested(5);
        rig.station().output().setStackInSlot(0, new ItemStack(Items.WHEAT, 3));
        rig.station().setWorking(false);
        var tag = new net.minecraft.nbt.CompoundTag();
        rig.bot().saveWithoutId(tag);
        FarmBotEntity copy = new FarmBotEntity(ModEntities.FARMBOT.get(), helper.getLevel());
        copy.load(tag);
        helper.assertTrue(copy.inventory().getStackInSlot(1).getCount() == 13, "Cargo not saved");
        helper.assertTrue(copy.batteryPercent() == 42, "Battery " + copy.batteryPercent());
        helper.assertTrue(copy.harvested() == 5 && copy.docked(), "Counters or dock not saved");
        helper.assertTrue(helper.absolutePos(STATION).equals(copy.stationPos()), "Station link not saved");
        var registries = helper.getLevel().registryAccess();
        var stationTag = rig.station().saveWithFullMetadata(registries);
        FarmBotStationBlockEntity station = new FarmBotStationBlockEntity(helper.absolutePos(STATION), rig.station().getBlockState());
        station.loadWithComponents(stationTag, registries);
        helper.assertTrue(station.owns(rig.bot().getUUID()), "Robot association not saved");
        helper.assertTrue(station.output().getStackInSlot(0).getCount() == 3, "Output not saved");
        helper.assertFalse(station.working(), "PAUSE not saved");
        helper.assertTrue(station.wholeFarm() && station.monitorId().equals(rig.station().monitorId()), "Crop Monitor choice not saved");
        helper.assertTrue(station.controllerLink().isPresent(), "Controller link not saved");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 2000)
    public static void twoRobotsShareOneMonitorWithoutDoubleHarvest(GameTestHelper helper) {
        Rig rig = rig(helper);
        BlockPos otherStation = new BlockPos(4, 1, 15);
        helper.setBlock(otherStation, ModBlocks.FARMBOT_STATION.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.NORTH));
        FarmBotStationBlockEntity second = helper.getBlockEntity(otherStation);
        FarmLinkService.link(helper.getLevel(), rig.controller(), second, 32);
        second.install(helper.getLevel(), FakePlayerFactory.getMinecraft(helper.getLevel()), FarmBotItem.create(100, 0, null));
        FarmBotEntity other = robot(helper, second);
        for (int x = 3; x <= 13; x += 2) plant(helper, new BlockPos(x, 1, 5), mature(Blocks.WHEAT));
        rig.monitor().scanner().requestPass();
        helper.succeedWhen(() -> {
            for (int x = 3; x <= 13; x += 2) {
                helper.assertTrue(((CropBlock) Blocks.WHEAT).getAge(helper.getBlockState(new BlockPos(x, 1, 5))) == 0, "Crop at x=" + x + " left");
            }
            helper.assertTrue(rig.bot().harvested() + other.harvested() == 6, "Harvests " + rig.bot().harvested() + " + " + other.harvested());
            helper.assertTrue(rig.bot().harvested() > 0 && other.harvested() > 0, "One robot did everything");
            helper.assertTrue(docked(rig.bot()) && docked(other), "Robots not home");
        });
    }
}
