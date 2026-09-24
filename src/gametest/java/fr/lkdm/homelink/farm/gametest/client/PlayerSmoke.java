package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pressKey;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay;
import fr.lkdm.homelink.farm.client.screen.CropMonitorScreen;
import fr.lkdm.homelink.farm.client.screen.FarmControllerScreen;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import fr.lkdm.homelink.farm.registry.ModDataComponents;
import fr.lkdm.homelink.farm.registry.ModItems;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;

/**
 * Plays HomeLink Farm like a player: every placement, click, sneak, key press, block break and
 * command goes through the real client input path (packets to the integrated server). Only
 * terrain preparation (farmland, wheat, one water source) is done server-side.
 * Run with {@code ./gradlew runClientSmoke -PsmokeScenario=player}.
 */
final class PlayerSmoke {
    static final int Y = SmokeScenario.GROUND + 1;
    static final BlockPos CONTROLLER = new BlockPos(0, Y, 4);
    static final BlockPos MONITOR = new BlockPos(8, Y, 4);
    static final BlockPos PUMP = new BlockPos(1, Y, 10);
    static final List<Integer> SPRINKLER_X = List.of(2, 5, 8, 11, 14);
    static final BlockPos SIXTH = new BlockPos(15, Y + 1, 10);
    static final BlockPos ZONE_A = new BlockPos(2, Y, 8);
    static final BlockPos ZONE_B = new BlockPos(14, Y, 12);
    /** Irrigated field: rows 8, 9, 11, 12 around the pipe row 10. Control field: rows 20..23. */
    static final int[] IRRIGATED_ROWS = {8, 9, 11, 12};
    static final int[] CONTROL_ROWS = {20, 21, 22, 23};
    static final int MEASURE_TICKS = 1600;
    static final int MEASURE_RANDOM_TICK_SPEED = 100;
    private static final int SLOT_CONTROLLER = 0, SLOT_MONITOR = 1, SLOT_CONNECTOR = 2, SLOT_PUMP = 3, SLOT_PIPE = 4,
            SLOT_SPRINKLER = 5, SLOT_HONEYCOMB = 6, SLOT_AXE = 7, SLOT_EMPTY = 8;

    private static volatile UUID controllerId;
    private static volatile long measureStart = -1;
    private static volatile boolean measuring;
    private static final AtomicInteger IRRIGATED_TICKS = new AtomicInteger();
    private static final AtomicInteger CONTROL_TICKS = new AtomicInteger();

    private PlayerSmoke() {
    }

    // ---- player input helpers (all go through the real client -> server path) ----------------

    static Minecraft mc() {
        return Minecraft.getInstance();
    }

    static void select(int slot) {
        mc().player.getInventory().selected = slot;
    }

    /** Right-click on a block face with the selected item, exactly like a mouse click. */
    static void click(BlockPos pos, Direction face) {
        Vec3 hit = Vec3.atCenterOf(pos).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        var result = mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, pos, false));
        HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PLAYER click {} {} with {} -> {}", pos, face, mc().player.getMainHandItem(), result);
    }

    /** Places the selected block on top of {@code ground}. */
    static void placeOn(BlockPos ground) {
        click(ground, Direction.UP);
    }

    static void sneak(boolean down) {
        mc().options.keyShift.setDown(down);
    }

    static void walkTo(double x, double z) {
        onServer(player -> {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), x, Y, z, 0, 30);
        });
    }

    /** Hovers (creative flight) for a screenshot, so the camera stays where it is placed. */
    static void camera(double x, double y, double z, float yaw, float pitch) {
        onServer(player -> {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        });
    }

    static Block clientBlock(BlockPos pos) {
        return mc().level.getBlockState(pos).getBlock();
    }

    static IrrigationVisual visual(BlockPos pos) {
        var state = mc().level.getBlockState(pos);
        return state.hasProperty(IrrigationVisual.PROPERTY) ? state.getValue(IrrigationVisual.PROPERTY) : null;
    }

    static boolean sprinklers(IrrigationVisual expected, int from, int to) {
        for (int i = from; i < to; i++) {
            if (visual(new BlockPos(SPRINKLER_X.get(i), Y + 1, 10)) != expected) return false;
        }
        return true;
    }

    static <T> T clientEntity(BlockPos pos, Class<T> type) {
        var entity = mc().level == null ? null : mc().level.getBlockEntity(pos);
        return type.isInstance(entity) ? type.cast(entity) : null;
    }

    static PumpStatus pumpStatus() {
        var pump = clientEntity(PUMP, IrrigationPumpBlockEntity.class);
        return pump == null ? null : pump.snapshot().status();
    }

    // ---- growth measurement ---------------------------------------------------------------------

    /** Counts real random-tick growth opportunities per field and keeps the crops at age 0 while measuring. */
    static void onCropGrow(CropGrowEvent.Pre event) {
        if (!measuring || !(event.getLevel() instanceof ServerLevel)) return;
        BlockPos pos = event.getPos();
        if (pos.getY() != Y || pos.getX() < 2 || pos.getX() > 14) return;
        boolean irrigated = contains(IRRIGATED_ROWS, pos.getZ());
        boolean control = contains(CONTROL_ROWS, pos.getZ());
        if (!irrigated && !control) return;
        (irrigated ? IRRIGATED_TICKS : CONTROL_TICKS).incrementAndGet();
        event.setResult(CropGrowEvent.Pre.Result.DO_NOT_GROW);
    }

    private static boolean contains(int[] rows, int z) {
        for (int row : rows) if (row == z) return true;
        return false;
    }

    // ---- scenario -------------------------------------------------------------------------------

    static void define() {
        NeoForge.EVENT_BUS.addListener(PlayerSmoke::onCropGrow);
        step("prepare terrain and inventory", () -> true, () -> onServer(player -> {
            ServerLevel level = player.serverLevel();
            for (int x = 2; x <= 14; x++) {
                for (int[] rows : new int[][] {IRRIGATED_ROWS, CONTROL_ROWS}) {
                    for (int z : rows) {
                        level.setBlockAndUpdate(new BlockPos(x, Y - 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                        level.setBlockAndUpdate(new BlockPos(x, Y, z), ((CropBlock) Blocks.WHEAT).getStateForAge(0));
                    }
                }
            }
            var inventory = player.getInventory();
            inventory.setItem(SLOT_CONTROLLER, new ItemStack(ModItems.FARM_CONTROLLER.get()));
            inventory.setItem(SLOT_MONITOR, new ItemStack(ModItems.CROP_MONITOR.get()));
            inventory.setItem(SLOT_CONNECTOR, new ItemStack(ModItems.FARM_CONNECTOR.get()));
            inventory.setItem(SLOT_PUMP, new ItemStack(ModItems.IRRIGATION_PUMP.get()));
            inventory.setItem(SLOT_PIPE, new ItemStack(ModItems.PIPES.getFirst().get(), 64));
            inventory.setItem(SLOT_SPRINKLER, new ItemStack(ModItems.COPPER_SPRINKLER.get(), 16));
            inventory.setItem(SLOT_HONEYCOMB, new ItemStack(Items.HONEYCOMB, 8));
            inventory.setItem(SLOT_AXE, new ItemStack(Items.IRON_AXE));
            inventory.setItem(SLOT_EMPTY, ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
        }));
        step("tooltips explain the blocks", () -> mc().player.getInventory().getItem(SLOT_PUMP).is(ModItems.IRRIGATION_PUMP.get()), () -> {
            var lines = mc().player.getInventory().getItem(SLOT_PUMP).getTooltipLines(
                    net.minecraft.world.item.Item.TooltipContext.of(mc().level), mc().player, net.minecraft.world.item.TooltipFlag.NORMAL);
            String expected = net.minecraft.network.chat.Component.translatable("tooltip.homelink_farm.irrigation_pump.2").getString();
            check(lines.stream().anyMatch(line -> line.getString().equals(expected)), "pump tooltip missing: " + lines);
        });
        walk(4.5, 7.5);

        // 1. Place the Farm Controller and the Crop Monitor like a player.
        step("place controller", () -> mc().player.getInventory().getItem(SLOT_CONTROLLER).is(ModItems.FARM_CONTROLLER.get()), () -> {
            select(SLOT_CONTROLLER);
            placeOn(CONTROLLER.below());
        });
        // NeoForge calls onLoad() (HomeCore registration) on the tick after placement.
        step("controller appears", () -> clientEntity(CONTROLLER, FarmControllerBlockEntity.class) != null, () -> { });
        pause(5);
        step("controller placed", () -> true, () -> onServer(player -> {
            var controller = (FarmControllerBlockEntity) player.level().getBlockEntity(CONTROLLER);
            check(controller.owner().map(owner -> owner.equals(player.getUUID())).orElse(false), "placer is not the owner");
            check(player.level().getBlockState(CONTROLLER).getValue(AbstractFarmDeviceBlock.FACING) == Direction.NORTH,
                    "controller front should face the player");
            check(DashboardAPI.devices(player.server).get(controller.deviceId()).isPresent(), "placed controller not in HomeCore");
            controllerId = controller.deviceId();
        }));
        step("place monitor", () -> controllerId != null, () -> {
            select(SLOT_MONITOR);
            placeOn(MONITOR.below());
        });
        step("monitor has default chunk zone", () -> clientEntity(MONITOR, CropMonitorBlockEntity.class) != null
                && clientEntity(MONITOR, CropMonitorBlockEntity.class).zone().isPresent(), () -> {
            CropZone zone = clientEntity(MONITOR, CropMonitorBlockEntity.class).zone().get();
            check(zone.equals(CropZone.chunkAround(MONITOR, 1, 1)), "default zone " + zone);
        });

        // 2. Farm Connector: select the controller, link the monitor, set zone A/B while sneaking.
        step("connector: select controller", () -> true, () -> {
            select(SLOT_CONNECTOR);
            click(CONTROLLER, Direction.SOUTH);
        });
        step("controller selected", () -> mc().player.getMainHandItem().has(ModDataComponents.SELECTED_CONTROLLER.get()),
                () -> click(MONITOR, Direction.SOUTH));
        step("monitor linked", () -> clientEntity(MONITOR, CropMonitorBlockEntity.class).controllerLink()
                .map(link -> link.controllerPos().equals(CONTROLLER)).orElse(false), () -> sneak(true));
        pause(4);
        step("zone position A", () -> mc().player.isShiftKeyDown(), () -> click(ZONE_A, Direction.UP));
        walk(11.5, 7.5);
        step("zone position B", () -> mc().player.getMainHandItem().has(ModDataComponents.ZONE_CORNER_A.get()), () -> click(ZONE_B, Direction.UP));
        step("zone preview while selecting", () -> mc().player.getMainHandItem().has(ModDataComponents.ZONE_CORNER_B.get()),
                () -> camera(8.5, Y + 6, 1.5, 0, 38));
        pause(15);
        step("screenshot zone preview", () -> true, () -> screenshot("player_zone_preview"));
        walk(11.5, 7.5);
        step("apply zone on monitor", () -> mc().player.getMainHandItem().has(ModDataComponents.ZONE_CORNER_B.get()),
                () -> click(MONITOR, Direction.SOUTH));
        step("zone applied", () -> clientEntity(MONITOR, CropMonitorBlockEntity.class).zone()
                .map(zone -> zone.equals(new CropZone(ZONE_A, ZONE_B))).orElse(false), () -> sneak(false));
        pause(4);

        // 3. Build the copper network by hand.
        walk(4.5, 7.5);
        step("place pump", () -> !mc().player.isShiftKeyDown(), () -> {
            select(SLOT_PUMP);
            placeOn(PUMP.below());
        });
        step("water under the pump", () -> clientBlock(PUMP) == ModBlocks.IRRIGATION_PUMP.get(),
                () -> onServer(player -> player.serverLevel().setBlockAndUpdate(PUMP.below(), Blocks.WATER.defaultBlockState())));
        step("place pipes 2..8", () -> true, () -> {
            select(SLOT_PIPE);
            for (int x = 2; x <= 8; x++) placeOn(new BlockPos(x, Y - 1, 10));
        });
        walk(11.5, 7.5);
        step("place pipes 9..14", () -> clientBlock(new BlockPos(8, Y, 10)) == ModBlocks.COPPER_PIPE.get(), () -> {
            for (int x = 9; x <= 14; x++) placeOn(new BlockPos(x, Y - 1, 10));
        });
        step("pipes connect visually", () -> clientBlock(new BlockPos(14, Y, 10)) == ModBlocks.COPPER_PIPE.get(), () -> {
            var first = mc().level.getBlockState(new BlockPos(2, Y, 10));
            check(first.getValue(PipeBlock.WEST) && first.getValue(PipeBlock.EAST), "pipe next to the pump is not connected: " + first);
            check(!first.getValue(PipeBlock.NORTH) && !first.getValue(PipeBlock.UP), "pipe has phantom connections: " + first);
        });
        step("place sprinklers 11, 14", () -> true, () -> {
            select(SLOT_SPRINKLER);
            placeOn(new BlockPos(11, Y, 10));
            placeOn(new BlockPos(14, Y, 10));
        });
        walk(4.5, 7.5);
        step("place sprinklers 2, 5, 8", () -> true, () -> {
            for (int x : List.of(2, 5, 8)) placeOn(new BlockPos(x, Y, 10));
        });
        step("link pump", () -> true, () -> {
            select(SLOT_CONNECTOR);
            click(CONTROLLER, Direction.SOUTH);
            click(PUMP, Direction.NORTH);
        });
        step("5 sprinklers irrigate", () -> pumpStatus() == PumpStatus.ACTIVE && sprinklers(IrrigationVisual.ACTIVE, 0, 5), () -> {
            var pipeUnderSprinkler = mc().level.getBlockState(new BlockPos(5, Y, 10));
            check(pipeUnderSprinkler.getValue(PipeBlock.UP), "pipe under a sprinkler should connect upwards");
        });
        pause(20);
        step("screenshot network", () -> true, () -> screenshot("player_network"));

        // 4. Open the monitor with an empty hand (real block use) and check irrigation.
        step("open monitor", () -> true, () -> {
            select(SLOT_EMPTY);
            click(MONITOR, Direction.SOUTH);
        });
        step("monitor screen", () -> mc().screen instanceof CropMonitorScreen, () -> pressKey("gui.homelink_farm.rescan"));
        step("52 crops irrigated", () -> clientEntity(MONITOR, CropMonitorBlockEntity.class).result()
                .map(result -> result.crops() == 52 && result.irrigated() == 52).orElse(false), () -> { });
        pause(10);
        step("screenshot monitor", () -> true, () -> screenshot("player_monitor"));
        step("show zone button", () -> true, () -> pressKey("gui.homelink_farm.zone.show"));
        step("zone shown", () -> mc().screen == null && fr.lkdm.homelink.farm.client.rendering.ZonePreview.showing(MONITOR),
                () -> camera(8.5, Y + 6, 1.5, 0, 38));
        pause(15);
        step("screenshot zone shown", () -> true, () -> screenshot("player_zone_shown"));
        walk(4.5, 7.5);

        // 5. The real key: I toggles the irrigation view.
        step("press I", () -> mc().screen == null, () -> KeyMapping.click(IrrigationOverlay.TOGGLE_KEY.getKey()));
        step("overlay on", () -> IrrigationOverlay.enabled() && IrrigationOverlay.data().sprinklers().size() >= 5, () -> { });
        pause(10);
        step("screenshot overlay", () -> true, () -> screenshot("player_overlay"));
        step("press I again", () -> true, () -> KeyMapping.click(IrrigationOverlay.TOGGLE_KEY.getKey()));
        step("overlay off", () -> !IrrigationOverlay.enabled(), () -> { });

        // 6. A 6th sprinkler overloads the network; breaking it restores irrigation.
        walk(11.5, 7.5);
        step("add 6th sprinkler", () -> true, () -> {
            select(SLOT_PIPE);
            placeOn(new BlockPos(15, Y - 1, 10));
            select(SLOT_SPRINKLER);
            placeOn(new BlockPos(15, Y, 10));
        });
        step("over capacity", () -> pumpStatus() == PumpStatus.OVER_CAPACITY && visual(SIXTH) == IrrigationVisual.ERROR
                && sprinklers(IrrigationVisual.ERROR, 0, 5), () -> { });
        pause(10);
        step("screenshot overloaded", () -> true, () -> screenshot("player_overloaded"));
        step("break 6th sprinkler", () -> true, () -> mc().gameMode.startDestroyBlock(SIXTH, Direction.UP));
        step("back to active", () -> pumpStatus() == PumpStatus.ACTIVE && sprinklers(IrrigationVisual.ACTIVE, 0, 5), () -> { });

        // 7. Copper care: honeycomb waxes, axe scrapes; oxidation never breaks the network.
        walk(4.5, 7.5);
        step("wax pipe with honeycomb", () -> true, () -> {
            select(SLOT_HONEYCOMB);
            click(new BlockPos(5, Y, 10), Direction.NORTH);
        });
        step("pipe waxed", () -> clientBlock(new BlockPos(5, Y, 10)) == ModBlocks.WAXED_COPPER_PIPE.get(), () -> onServer(player -> {
            var level = player.serverLevel();
            BlockPos pos = new BlockPos(7, Y, 10);
            level.setBlockAndUpdate(pos, ModBlocks.OXIDIZED_COPPER_PIPE.get().withPropertiesOf(level.getBlockState(pos)));
        }));
        step("scrape oxidized pipe with axe", () -> clientBlock(new BlockPos(7, Y, 10)) == ModBlocks.OXIDIZED_COPPER_PIPE.get(), () -> {
            select(SLOT_AXE);
            click(new BlockPos(7, Y, 10), Direction.NORTH);
        });
        step("pipe scraped", () -> clientBlock(new BlockPos(7, Y, 10)) == ModBlocks.WEATHERED_COPPER_PIPE.get(), () -> { });
        step("network unaffected by copper state", () -> pumpStatus() == PumpStatus.ACTIVE && sprinklers(IrrigationVisual.ACTIVE, 0, 5), () -> { });

        // 8. Break a pipe by hand: downstream sprinklers stop, repairing reconnects them.
        step("break pipe 3", () -> true, () -> mc().gameMode.startDestroyBlock(new BlockPos(3, Y, 10), Direction.NORTH));
        step("downstream stopped", () -> sprinklers(IrrigationVisual.OFF, 1, 5) && sprinklers(IrrigationVisual.ACTIVE, 0, 1), () -> { });
        step("repair pipe", () -> true, () -> {
            select(SLOT_PIPE);
            placeOn(new BlockPos(3, Y - 1, 10));
        });
        step("reconnected", () -> sprinklers(IrrigationVisual.ACTIVE, 0, 5), () -> { });

        // 8b. Hang a sprinkler under an overhead pipe: click the underside of the pipe.
        walk(11.5, 7.5);
        BlockPos last = new BlockPos(14, Y + 1, 10);
        step("remove standing sprinkler 14", () -> true, () -> mc().gameMode.startDestroyBlock(last, Direction.UP));
        step("build overhead pipe", () -> clientBlock(last) == Blocks.AIR, () -> {
            select(SLOT_PIPE);
            placeOn(new BlockPos(13, Y, 10));
            placeOn(new BlockPos(13, Y + 1, 10));
            click(new BlockPos(13, Y + 2, 10), Direction.EAST);
        });
        step("hang sprinkler under the pipe", () -> clientBlock(new BlockPos(14, Y + 2, 10)) == ModBlocks.COPPER_PIPE.get(), () -> {
            select(SLOT_SPRINKLER);
            click(new BlockPos(14, Y + 2, 10), Direction.DOWN);
        });
        step("hanging sprinkler irrigates", () -> clientBlock(last) == ModBlocks.COPPER_SPRINKLER.get()
                && fr.lkdm.homelink.farm.block.CopperSprinklerBlock.isHanging(mc().level.getBlockState(last))
                && pumpStatus() == PumpStatus.ACTIVE && sprinklers(IrrigationVisual.ACTIVE, 0, 5), () -> {
            check(mc().level.getBlockState(new BlockPos(14, Y + 2, 10)).getValue(PipeBlock.DOWN), "overhead pipe not connected downwards");
            camera(15.5, Y + 2.5, 5.5, 20, 20);
        });
        pause(15);
        step("screenshot hanging", () -> true, () -> screenshot("player_hanging"));
        walk(11.5, 7.5);

        // 9. Measure the growth bonus in real play (command typed like a player).
        step("gamerule randomTickSpeed", () -> true,
                () -> mc().player.connection.sendCommand("gamerule randomTickSpeed " + MEASURE_RANDOM_TICK_SPEED));
        pause(10);
        step("start measuring", () -> true, () -> {
            IRRIGATED_TICKS.set(0);
            CONTROL_TICKS.set(0);
            measureStart = serverTime();
            measuring = true;
        });
        step("measuring " + MEASURE_TICKS + " ticks", () -> serverTime() - measureStart >= MEASURE_TICKS, () -> {
            measuring = false;
            double ratio = IRRIGATED_TICKS.get() / (double) Math.max(1, CONTROL_TICKS.get());
            double expectedControl = 52.0 * MEASURE_RANDOM_TICK_SPEED / 4096 * MEASURE_TICKS;
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PLAYER growth opportunities irrigated={} control={} ratio={} (expected control ~{}, ratio 1.20)",
                    IRRIGATED_TICKS.get(), CONTROL_TICKS.get(), String.format("%.3f", ratio), Math.round(expectedControl));
            check(ratio > 1.12 && ratio < 1.28, "growth bonus ratio " + ratio + " is not ~1.20");
            mc().player.connection.sendCommand("gamerule randomTickSpeed 3");
        });

        // 10. Rename through the screen, then quit and reload the world.
        walk(4.5, 7.5);
        step("open controller", () -> true, () -> {
            select(SLOT_EMPTY);
            click(CONTROLLER, Direction.SOUTH);
        });
        step("rename controller", () -> mc().screen instanceof FarmControllerScreen, () -> {
            EditBox name = mc().screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
            name.setValue("Player Farm");
            pressKey("gui.homelink_farm.rename");
        });
        step("renamed", () -> "Player Farm".equals(clientEntity(CONTROLLER, FarmControllerBlockEntity.class).customName()), () -> {
            mc().player.closeContainer();
            screenshot("player_before_quit");
        });
        pause(10);
        step("save and quit", () -> true, () -> {
            mc().level.disconnect();
            mc().disconnect(new TitleScreen());
        });
        step("reopen world", () -> mc().level == null && mc().screen instanceof TitleScreen,
                () -> mc().createWorldOpenFlows().openWorld(ClientSmoke.levelId, () -> { }));
        step("world reloaded", () -> mc().player != null && mc().getSingleplayerServer() != null
                && clientEntity(CONTROLLER, FarmControllerBlockEntity.class) != null
                && clientEntity(MONITOR, CropMonitorBlockEntity.class) != null, () -> { });
        step("state survived", () -> pumpStatus() == PumpStatus.ACTIVE && sprinklers(IrrigationVisual.ACTIVE, 0, 5), () -> {
            var controller = clientEntity(CONTROLLER, FarmControllerBlockEntity.class);
            var monitor = clientEntity(MONITOR, CropMonitorBlockEntity.class);
            check(controller.deviceId().equals(controllerId), "controller UUID changed after reload");
            check(controller.customName().equals("Player Farm"), "name lost after reload");
            check(controller.linkedComponents().size() == 2, "links lost after reload: " + controller.linkedComponents().size());
            check(monitor.zone().map(zone -> zone.equals(new CropZone(ZONE_A, ZONE_B))).orElse(false), "zone lost after reload");
            check(clientBlock(new BlockPos(5, Y, 10)) == ModBlocks.WAXED_COPPER_PIPE.get(), "waxed pipe lost after reload");
        });
        pause(20);
        step("screenshot reloaded", () -> true, () -> screenshot("player_reloaded"));

        // 11. Breaking the controller by hand removes it from HomeCore and unlinks its devices.
        walk(4.5, 7.5);
        step("break controller", () -> true, () -> mc().gameMode.startDestroyBlock(CONTROLLER, Direction.SOUTH));
        step("controller gone", () -> clientBlock(CONTROLLER) == Blocks.AIR
                && clientEntity(MONITOR, CropMonitorBlockEntity.class).controllerLink().isEmpty(), () -> onServer(player ->
                check(DashboardAPI.devices(player.server).get(controllerId).isEmpty(), "broken controller still registered in HomeCore")));
        step("done", () -> true, () -> HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PLAYER_OK"));
    }

    private static void walk(double x, double z) {
        step("walk to " + x + "," + z, () -> true, () -> walkTo(x, z));
        pause(5);
    }

    private static long serverTime() {
        var server = mc().getSingleplayerServer();
        return server == null ? -1 : server.overworld().getGameTime();
    }
}
