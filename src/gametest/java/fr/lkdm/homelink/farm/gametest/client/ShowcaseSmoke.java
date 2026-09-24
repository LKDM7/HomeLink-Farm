package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;
import static fr.lkdm.homelink.farm.gametest.client.SmokeScenario.GROUND;

import fr.lkdm.homelink.farm.block.AbstractFarmDeviceBlock;
import fr.lkdm.homelink.farm.block.IrrigationPumpBlock;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Close-up of every device model: a linked controller, monitor and submerged pump next to an
 * unlinked dry pump and a sprinkler, photographed by day and by night (blinking lights glow).
 */
final class ShowcaseSmoke {
    private static final BlockPos CONTROLLER = new BlockPos(-20, GROUND + 1, -20);
    private static final BlockPos MONITOR = new BlockPos(-18, GROUND + 1, -20);
    /** Sits in a 3 x 3 pond dug into the ground. */
    private static final BlockPos WET_PUMP = new BlockPos(-16, GROUND, -20);
    private static final BlockPos DRY_PUMP = new BlockPos(-13, GROUND + 1, -20);
    private static final BlockPos SPRINKLER = new BlockPos(-11, GROUND + 1, -20);

    private ShowcaseSmoke() {
    }

    static void define() {
        step("build showcase", () -> true, () -> onServer(player -> {
            ServerLevel level = player.serverLevel();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) level.setBlockAndUpdate(WET_PUMP.offset(x, 0, z), Blocks.WATER.defaultBlockState());
            }
            level.setBlockAndUpdate(WET_PUMP, facingSouth(ModBlocks.IRRIGATION_PUMP.get()).setValue(IrrigationPumpBlock.WATERLOGGED, true));
            level.setBlockAndUpdate(CONTROLLER, facingSouth(ModBlocks.FARM_CONTROLLER.get()));
            level.setBlockAndUpdate(MONITOR, facingSouth(ModBlocks.CROP_MONITOR.get()));
            level.setBlockAndUpdate(DRY_PUMP, facingSouth(ModBlocks.IRRIGATION_PUMP.get()));
            level.setBlockAndUpdate(SPRINKLER, ModBlocks.COPPER_SPRINKLER.get().defaultBlockState());
            var controller = (FarmControllerBlockEntity) level.getBlockEntity(CONTROLLER);
            FarmLinkService.link(level, controller, (CropMonitorBlockEntity) level.getBlockEntity(MONITOR), 32);
            FarmLinkService.link(level, controller, (IrrigationPumpBlockEntity) level.getBlockEntity(WET_PUMP), 32);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(level, -15.5, GROUND + 2.5, -14.5, 180, 22);
        }));
        step("lights on", () -> linked(CONTROLLER) && linked(MONITOR) && linked(WET_PUMP), () -> {
            check(!linked(DRY_PUMP), "Unlinked pump shows linked lights");
            check(Minecraft.getInstance().level.getFluidState(WET_PUMP).isSource(), "Pump is not submerged");
        });
        pause(20);
        step("showcase day screenshot", () -> true, () -> screenshot("showcase_day"));
        step("night", () -> true, () -> onServer(player -> player.serverLevel().setDayTime(18000)));
        pause(20);
        step("showcase night screenshot", () -> true, () -> screenshot("showcase_night"));
        step("morning", () -> true, () -> onServer(player -> player.serverLevel().setDayTime(1000)));
        closeUp("controller", CONTROLLER);
        closeUp("monitor", MONITOR);
        closeUp("pump", DRY_PUMP);
        closeUp("sprinkler", SPRINKLER);
    }

    private static void closeUp(String name, BlockPos pos) {
        step("model camera " + name, () -> true, () -> onServer(player ->
                player.teleportTo(player.serverLevel(), pos.getX() + 2.5, GROUND + 1.2,
                        pos.getZ() + 3.5, 146, 22)));
        pause(20);
        step("model detail " + name, () -> true, () -> screenshot("model_" + name));
    }

    private static BlockState facingSouth(Block block) {
        return block.defaultBlockState().setValue(AbstractFarmDeviceBlock.FACING, Direction.SOUTH);
    }

    private static boolean linked(BlockPos pos) {
        BlockState state = Minecraft.getInstance().level.getBlockState(pos);
        return state.hasProperty(AbstractFarmDeviceBlock.LINKED) && state.getValue(AbstractFarmDeviceBlock.LINKED);
    }
}
