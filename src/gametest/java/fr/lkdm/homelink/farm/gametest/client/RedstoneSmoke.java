package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pressKey;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.client.screen.IrrigationPumpScreen;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.irrigation.RedstoneMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.RedstoneLampBlock;

/** Phase 7 client checks: comparator reading the Crop Monitor, pump redstone mode from its screen. */
final class RedstoneSmoke {
    static final BlockPos COMPARATOR = SmokeScenario.MONITOR.west();
    static final BlockPos LAMP = COMPARATOR.west();

    private RedstoneSmoke() {
    }

    static void define() {
        step("comparator + lamp", () -> true, () -> onServer(player -> {
            var level = player.serverLevel();
            level.setBlockAndUpdate(COMPARATOR, Blocks.COMPARATOR.defaultBlockState().setValue(ComparatorBlock.FACING, Direction.EAST));
            level.setBlockAndUpdate(LAMP, Blocks.REDSTONE_LAMP.defaultBlockState());
            var controller = (FarmControllerBlockEntity) level.getBlockEntity(SmokeScenario.CONTROLLER);
            FarmLinkService.link(level, controller, (IrrigationPumpBlockEntity) level.getBlockEntity(IrrigationSmoke.FIELD_PUMP), 32);
            player.teleportTo(level, -0.5, SmokeScenario.GROUND + 4, -3.5, -20, 35);
        }));
        step("lamp lit by the monitor", () -> {
            var state = Minecraft.getInstance().level.getBlockState(LAMP);
            return state.is(Blocks.REDSTONE_LAMP) && state.getValue(RedstoneLampBlock.LIT);
        }, () -> {
            int signal = Minecraft.getInstance().level.getSignal(COMPARATOR.west(), Direction.WEST);
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_SMOKE comparator output {}", signal);
        });
        pause(10);
        step("comparator screenshot", () -> true, () -> screenshot("comparator"));
        step("open pump", () -> true, () -> onServer(player -> {
            player.teleportTo(player.serverLevel(), IrrigationSmoke.FIELD_PUMP.getX() + 0.5, IrrigationSmoke.FIELD_PUMP.getY() + 1,
                    IrrigationSmoke.FIELD_PUMP.getZ() - 2.5, 0, 30);
            player.openMenu((IrrigationPumpBlockEntity) player.level().getBlockEntity(IrrigationSmoke.FIELD_PUMP), IrrigationSmoke.FIELD_PUMP);
        }));
        step("pump screen", () -> Minecraft.getInstance().screen instanceof IrrigationPumpScreen, () -> { });
        step("cycle redstone mode", () -> true, () -> pressKey("gui.homelink_farm.pump.redstone_cycle"));
        step("mode changed", () -> pump().redstoneMode() == RedstoneMode.RUN_WHEN_POWERED, () -> { });
        pause(25);
        step("pump redstone screenshot", () -> true, () -> {
            check(pump().snapshot().status().name().equals("REDSTONE_STOPPED"), "unpowered pump should be stopped: " + pump().snapshot().status());
            screenshot("pump_redstone");
        });
        step("restore mode", () -> true, () -> {
            pressKey("gui.homelink_farm.pump.redstone_cycle");
            pressKey("gui.homelink_farm.pump.redstone_cycle");
        });
        step("mode restored", () -> pump().redstoneMode() == RedstoneMode.IGNORED, () -> { });
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
    }

    private static IrrigationPumpBlockEntity pump() {
        return (IrrigationPumpBlockEntity) Minecraft.getInstance().level.getBlockEntity(IrrigationSmoke.FIELD_PUMP);
    }
}
