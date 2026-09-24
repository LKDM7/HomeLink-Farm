package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.*;
import static fr.lkdm.homelink.farm.gametest.client.SmokeScenario.GROUND;

import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** Close, HUD-free captures dedicated to UV seams, coplanar faces and state textures. */
final class TextureSmoke {
    private static final BlockPos CONTROLLER = new BlockPos(0, GROUND + 1, 0);
    private static final BlockPos MONITOR = CONTROLLER.east(6);
    private static final BlockPos PUMP = CONTROLLER.east(12);
    private static final BlockPos SPRINKLER = CONTROLLER.east(18);
    private static final BlockPos HANGING = CONTROLLER.east(24).above(3);
    private static final BlockPos PIPES = CONTROLLER.south(10);

    private TextureSmoke() { }

    static void define() {
        step("texture fixtures", () -> true, () -> onServer(player -> {
            var level = player.serverLevel();
            level.setDayTime(1000);
            for (int x = -2; x <= 28; x++) {
                for (int z = -2; z <= 13; z++) level.setBlockAndUpdate(new BlockPos(x, GROUND, z), Blocks.SMOOTH_STONE.defaultBlockState());
            }
            level.setBlockAndUpdate(CONTROLLER, ModBlocks.FARM_CONTROLLER.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(MONITOR, ModBlocks.CROP_MONITOR.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(PUMP, ModBlocks.IRRIGATION_PUMP.get().defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(SPRINKLER, ModBlocks.COPPER_SPRINKLER.get().defaultBlockState());
            level.setBlockAndUpdate(HANGING.above(), ModBlocks.COPPER_PIPE.get().defaultBlockState());
            level.setBlockAndUpdate(HANGING, ModBlocks.COPPER_SPRINKLER.get().defaultBlockState().setValue(CopperSprinklerBlock.HANGING, true));
            var controller = (FarmControllerBlockEntity) level.getBlockEntity(CONTROLLER);
            FarmLinkService.link(level, controller, (CropMonitorBlockEntity) level.getBlockEntity(MONITOR), 32);
            FarmLinkService.link(level, controller, (IrrigationPumpBlockEntity) level.getBlockEntity(PUMP), 32);
            for (int i = 0; i < ModBlocks.PIPES.size(); i++) {
                var pipe = ModBlocks.PIPES.get(i).get().defaultBlockState();
                BlockPos p = PIPES.east(i * 3);
                level.setBlockAndUpdate(p, pipe);
                level.setBlockAndUpdate(p.above(), pipe);
                level.setBlockAndUpdate(p.east(), pipe);
                level.setBlockAndUpdate(p.north(), pipe);
            }
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }));
        step("texture camera settings", () -> Minecraft.getInstance().screen == null, () -> {
            Minecraft.getInstance().options.hideGui = true;
            Minecraft.getInstance().options.fov().set(30);
        });
        pause(25);
        for (var entry : java.util.Map.of("controller", CONTROLLER, "monitor", MONITOR, "pump", PUMP,
                "sprinkler", SPRINKLER, "hanging", HANGING).entrySet()) {
            capture(entry.getKey() + "_front", entry.getValue(), 2, 3, 146, 20);
            capture(entry.getKey() + "_back", entry.getValue(), -2, -3, -34, 20);
        }
        for (int i = 0; i < ModBlocks.PIPES.size(); i++) capture("pipe_" + i, PIPES.east(i * 3).above(), 2, 3, 146, 25);
        capture("hanging_underneath", HANGING, 2, 3, 146, -15, -1.8);
        step("pump off", () -> true, () -> onServer(player ->
                ((IrrigationPumpBlockEntity) player.serverLevel().getBlockEntity(PUMP)).setEnabled(false)));
        capture("pump_off", PUMP, 2, 3, 146, 20);
        step("pump supplied", () -> true, () -> onServer(player -> {
            var level = player.serverLevel();
            // Contained source under the plinth: inspect the active panel without flowing water hiding it.
            level.setBlockAndUpdate(PUMP.below(), Blocks.WATER.defaultBlockState());
            ((IrrigationPumpBlockEntity) level.getBlockEntity(PUMP)).setEnabled(true);
            level.setBlockAndUpdate(PUMP.east(), ModBlocks.COPPER_PIPE.get().defaultBlockState());
            level.setBlockAndUpdate(PUMP.east().above(), ModBlocks.COPPER_SPRINKLER.get().defaultBlockState());
        }));
        pause(25);
        capture("pump_active", PUMP, 2, 3, 146, 20);
        capture("sprinkler_active", PUMP.east().above(), 2, 3, 146, 20);
        step("night textures", () -> true, () -> onServer(player -> player.serverLevel().setDayTime(18000)));
        capture("monitor_night_a", MONITOR, 2, 3, 146, 20);
        pause(5);
        capture("monitor_night_b", MONITOR, 2, 3, 146, 20);
        capture("controller_night", CONTROLLER, 2, 3, 146, 20);
    }

    private static void capture(String name, BlockPos pos, double dx, double dz, float yaw, float pitch) {
        capture(name, pos, dx, dz, yaw, pitch, .3);
    }

    private static void capture(String name, BlockPos pos, double dx, double dz, float yaw, float pitch, double cameraHeight) {
        step("camera " + name, () -> true, () -> {
            var clientPlayer = Minecraft.getInstance().player;
            clientPlayer.getAbilities().flying = true;
            clientPlayer.setNoGravity(true);
            onServer(player -> {
                player.getAbilities().flying = true;
                player.setNoGravity(true);
                player.onUpdateAbilities();
                player.teleportTo(player.serverLevel(), pos.getX() + .5 + dx, pos.getY() + cameraHeight,
                        pos.getZ() + .5 + dz, yaw, pitch);
            });
        });
        pause(12);
        step("capture " + name, () -> true, () -> screenshot("texture_" + name));
    }
}
