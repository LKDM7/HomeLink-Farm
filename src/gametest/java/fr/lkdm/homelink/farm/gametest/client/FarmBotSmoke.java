package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pressKey;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;
import static fr.lkdm.homelink.farm.gametest.client.SmokeScenario.GROUND;

import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.client.screen.FarmBotStationScreen;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.item.FarmBotItem;
import fr.lkdm.homelink.farm.menu.FarmBotStationMenu;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.Vec3;

/**
 * FarmBot and its station in a real client: docked close-up, station screen (status and output
 * views, and without a robot), the robot driving and harvesting in a wheat field, and its status
 * light at night. Run alone with {@code ./gradlew runClientSmoke -PsmokeScenario=farmbot}.
 */
final class FarmBotSmoke {
    static final BlockPos STATION = new BlockPos(46, GROUND + 1, 52);
    static final BlockPos DOCK = STATION.north();
    static final BlockPos EMPTY_STATION = new BlockPos(50, GROUND + 1, 52);
    static final BlockPos CONTROLLER = new BlockPos(40, GROUND + 1, 52);
    static final BlockPos MONITOR = new BlockPos(41, GROUND + 1, 52);

    private FarmBotSmoke() {
    }

    static void define() {
        step("build farmbot farm", () -> true, () -> onServer(player -> {
            ServerLevel level = player.serverLevel();
            for (int x = 42; x <= 50; x++) {
                for (int z = 42; z <= 47; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, GROUND, z), Blocks.FARMLAND.defaultBlockState());
                    boolean ripe = (x + z) % 3 == 0;
                    level.setBlockAndUpdate(new BlockPos(x, GROUND + 1, z), ((CropBlock) Blocks.WHEAT).getStateForAge(ripe ? 7 : 3));
                }
            }
            level.setBlockAndUpdate(CONTROLLER, facing(ModBlocks.FARM_CONTROLLER.get().defaultBlockState(), Direction.NORTH));
            level.setBlockAndUpdate(MONITOR, facing(ModBlocks.CROP_MONITOR.get().defaultBlockState(), Direction.NORTH));
            level.setBlockAndUpdate(STATION, facing(ModBlocks.FARMBOT_STATION.get().defaultBlockState(), Direction.NORTH));
            level.setBlockAndUpdate(EMPTY_STATION, facing(ModBlocks.FARMBOT_STATION.get().defaultBlockState(), Direction.NORTH));
            var controller = (FarmControllerBlockEntity) level.getBlockEntity(CONTROLLER);
            var monitor = (CropMonitorBlockEntity) level.getBlockEntity(MONITOR);
            var station = (FarmBotStationBlockEntity) level.getBlockEntity(STATION);
            monitor.setCustomName("Wheat Rows");
            monitor.setZone(new CropZone(new BlockPos(42, GROUND + 1, 42), new BlockPos(50, GROUND + 1, 47)));
            FarmLinkService.link(level, controller, monitor, 32);
            FarmLinkService.link(level, controller, station, 32);
            station.setCustomName("Wheat Station");
            // Keep the robot home until its close-up and the screens are captured.
            station.setWorking(false);
            var result = station.install(level, player, FarmBotItem.create(100, 0, null));
            check(result.success(), "Robot install failed: " + result);
            station.output().setStackInSlot(0, new ItemStack(Items.WHEAT, 23));
            station.output().setStackInSlot(1, new ItemStack(Items.WHEAT_SEEDS, 17));
            station.output().setStackInSlot(2, new ItemStack(Items.CARROT, 9));
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            look(player, new Vec3(48.6, GROUND + 1.7, 49.3), Vec3.atBottomCenterOf(DOCK).add(0, 0.3, 0));
        }));
        step("robot visible", () -> robot() != null && robot().docked(), () -> { });
        pause(30);
        step("docked close-up", () -> true, () -> screenshot("farmbot_docked"));
        openStation(STATION);
        step("station status screenshot", () -> true, () -> {
            check(station(STATION).hasRobot(), "Station shows no robot");
            screenshot("farmbot_station");
        });
        step("output view", () -> true, () -> pressKey("gui.homelink_farm.farmbot.output"));
        pause(10);
        step("station output screenshot", () -> true, () -> {
            var menu = (FarmBotStationMenu) ((FarmBotStationScreen) Minecraft.getInstance().screen).getMenu();
            check(menu.slotsVisible() && menu.getSlot(0).getItem().is(Items.WHEAT), "Output slots not shown");
            screenshot("farmbot_station_output");
        });
        step("back to status", () -> true, () -> pressKey("gui.homelink_farm.farmbot.back"));
        step("start", () -> true, () -> pressKey("gui.homelink_farm.farmbot.start"));
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
        step("watch the field", () -> true, () -> onServer(player ->
                look(player, new Vec3(46.5, GROUND + 5, 53.5), new Vec3(46.5, GROUND + 1, 45))));
        step("robot working", () -> robot() != null && robot().displayedState() == FarmBotState.MOVING, () -> { });
        pause(25);
        step("field screenshot", () -> true, () -> screenshot("farmbot_field"));
        step("robot harvesting", () -> robot() != null && robot().displayedState() == FarmBotState.HARVESTING, () -> screenshot("farmbot_harvesting"));
        step("robot back", () -> robot() != null && robot().displayedState() == FarmBotState.IDLE
                || robot() != null && robot().displayedState() == FarmBotState.CHARGING && robot().docked(), () -> { });
        step("front camera", () -> true, () -> onServer(player ->
                look(player, new Vec3(47.9, GROUND + 1.9, 49.6), Vec3.atBottomCenterOf(DOCK).add(0, 0.3, 0))));
        pause(30);
        step("front close-up", () -> true, () -> {
            check(Math.abs(Mth.wrapDegrees(robot().yBodyRot - Direction.NORTH.toYRot())) < 1, "Docked robot not facing away from the station");
            screenshot("farmbot_front");
        });
        step("night", () -> true, () -> onServer(player -> {
            player.serverLevel().setDayTime(18000);
            look(player, new Vec3(48.6, GROUND + 1.7, 49.3), Vec3.atBottomCenterOf(DOCK).add(0, 0.3, 0));
        }));
        pause(30);
        step("night close-up", () -> true, () -> screenshot("farmbot_night"));
        step("morning", () -> true, () -> onServer(player -> player.serverLevel().setDayTime(1000)));
        openStation(STATION);
        step("station after harvest", () -> station(STATION).snapshot().map(robot -> robot.harvested() > 0).orElse(false), () -> {
            screenshot("farmbot_station_after");
            Minecraft.getInstance().player.closeContainer();
        });
        step("station front camera", () -> true, () -> onServer(player -> {
            player.getInventory().setItem(0, new ItemStack(fr.lkdm.homelink.farm.registry.ModItems.FARMBOT.get()));
            player.getInventory().setItem(1, new ItemStack(fr.lkdm.homelink.farm.registry.ModItems.FARMBOT_STATION.get()));
            player.getInventory().selected = 2;
            look(player, new Vec3(51.9, GROUND + 2.0, 49.6), Vec3.atCenterOf(EMPTY_STATION).add(0, -0.1, 0));
        }));
        pause(30);
        step("station front screenshot", () -> true, () -> screenshot("farmbot_station_model"));
        openStation(EMPTY_STATION);
        step("empty station screenshot", () -> true, () -> {
            check(!station(EMPTY_STATION).hasRobot(), "Empty station claims a robot");
            screenshot("farmbot_station_empty");
            Minecraft.getInstance().player.closeContainer();
        });
    }

    private static void openStation(BlockPos pos) {
        step("open station", () -> true, () -> onServer(player -> player.openMenu((FarmBotStationBlockEntity) player.level().getBlockEntity(pos), pos)));
        step("station screen", () -> Minecraft.getInstance().screen instanceof FarmBotStationScreen, () -> { });
        pause(10);
    }

    private static FarmBotStationBlockEntity station(BlockPos pos) {
        return (FarmBotStationBlockEntity) Minecraft.getInstance().level.getBlockEntity(pos);
    }

    private static FarmBotEntity robot() {
        var level = Minecraft.getInstance().level;
        if (level == null) return null;
        var robots = level.getEntitiesOfClass(FarmBotEntity.class, new net.minecraft.world.phys.AABB(STATION).inflate(24));
        return robots.isEmpty() ? null : robots.getFirst();
    }

    private static net.minecraft.world.level.block.state.BlockState facing(net.minecraft.world.level.block.state.BlockState state, Direction direction) {
        return state.setValue(AbstractFarmDeviceBlock.FACING, direction);
    }

    /** Hovers at {@code eye} looking at {@code target}. */
    private static void look(ServerPlayer player, Vec3 eye, Vec3 target) {
        Vec3 delta = target.subtract(eye);
        float yaw = (float) (Mth.atan2(delta.z, delta.x) * Mth.RAD_TO_DEG) - 90.0F;
        float pitch = (float) -(Mth.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * Mth.RAD_TO_DEG);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }
}
