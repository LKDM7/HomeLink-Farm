package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;
import static fr.lkdm.homelink.farm.gametest.client.SmokeScenario.GROUND;

import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay;
import fr.lkdm.homelink.farm.client.screen.IrrigationPumpScreen;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Phase 4 client checks: a real copper network with 5, then 6 sprinklers. */
final class IrrigationSmoke {
    static final BlockPos PUMP = new BlockPos(12, GROUND + 1, 12);
    static final List<Integer> SPRINKLER_X = List.of(13, 15, 17, 19, 21);
    static final BlockPos SIXTH = new BlockPos(23, GROUND + 2, 12);

    private IrrigationSmoke() {
    }

    static void define() {
        step("build irrigation", () -> true, () -> onServer(player -> {
            ServerLevel level = player.serverLevel();
            level.setBlockAndUpdate(PUMP.below(), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(PUMP, ModBlocks.IRRIGATION_PUMP.get().defaultBlockState());
            for (int x = 13; x <= 23; x++) {
                Block pipe = switch (x) {
                    case 14 -> ModBlocks.EXPOSED_COPPER_PIPE.get();
                    case 16 -> ModBlocks.WEATHERED_COPPER_PIPE.get();
                    case 18 -> ModBlocks.OXIDIZED_COPPER_PIPE.get();
                    case 20 -> ModBlocks.WAXED_COPPER_PIPE.get();
                    default -> ModBlocks.COPPER_PIPE.get();
                };
                place(level, new BlockPos(x, GROUND + 1, 12), pipe);
            }
            // A vertical riser to show up/down connections.
            place(level, new BlockPos(23, GROUND + 2, 13), ModBlocks.COPPER_PIPE.get());
            place(level, new BlockPos(23, GROUND + 1, 13), ModBlocks.COPPER_PIPE.get());
            for (int x : SPRINKLER_X) place(level, new BlockPos(x, GROUND + 2, 12), ModBlocks.COPPER_SPRINKLER.get());
            var pump = (IrrigationPumpBlockEntity) level.getBlockEntity(PUMP);
            pump.setOwner(player.getUUID(), player.getGameProfile().getName());
            pump.setCustomName("Pump #1");
            player.teleportTo(level, 17.5, GROUND + 5, 7.5, 0, 38);
        }));
        step("5 sprinklers active", () -> pumpStatus() == PumpStatus.ACTIVE && allSprinklers(IrrigationVisual.ACTIVE), () -> { });
        pause(30);
        step("irrigation screenshot", () -> true, () -> screenshot("irrigation_5"));
        openPump();
        step("pump screenshot", () -> true, () -> {
            check(clientPump().snapshot().sprinklers() == 5 && clientPump().snapshot().capacity() == 5, "pump should show 5 / 5");
            screenshot("pump_5");
        });
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
        step("add 6th sprinkler", () -> true, () -> onServer(player -> place(player.serverLevel(), SIXTH, ModBlocks.COPPER_SPRINKLER.get())));
        step("over capacity", () -> pumpStatus() == PumpStatus.OVER_CAPACITY && allSprinklers(IrrigationVisual.ERROR), () -> { });
        pause(10);
        step("overloaded screenshot", () -> true, () -> screenshot("irrigation_6"));
        openPump();
        step("pump overloaded screenshot", () -> true, () -> {
            check(clientPump().snapshot().sprinklers() == 6, "pump should count 6 sprinklers");
            screenshot("pump_6");
        });
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
        step("remove 6th sprinkler", () -> true, () -> onServer(player -> player.serverLevel().destroyBlock(SIXTH, false)));
        step("back to active", () -> pumpStatus() == PumpStatus.ACTIVE && allSprinklers(IrrigationVisual.ACTIVE), () -> { });
        defineFieldIrrigation();
    }

    /** Field pump south of the wheat field: raised sprinklers along z = 10 cover rows z = 8..9. */
    static final BlockPos FIELD_PUMP = new BlockPos(1, GROUND + 1, 10);

    static void defineFieldIrrigation() {
        step("irrigate field", () -> true, () -> onServer(player -> {
            ServerLevel level = player.serverLevel();
            level.setBlockAndUpdate(FIELD_PUMP.below(), Blocks.WATER.defaultBlockState());
            level.setBlockAndUpdate(FIELD_PUMP, ModBlocks.IRRIGATION_PUMP.get().defaultBlockState());
            for (int x = 2; x <= 9; x++) place(level, new BlockPos(x, GROUND + 1, 10), ModBlocks.COPPER_PIPE.get());
            for (int x = 3; x <= 9; x += 3) place(level, new BlockPos(x, GROUND + 2, 10), ModBlocks.COPPER_SPRINKLER.get());
            ((fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity) level.getBlockEntity(SmokeScenario.MONITOR)).scanner().requestPass();
            SmokeScenario.lookAtField(player);
        }));
        step("field irrigated", () -> SmokeScenario.clientMonitor().result().map(result -> result.irrigated() == 16).orElse(false), () -> { });
        SmokeScenario.openMonitor();
        step("monitor irrigation screenshot", () -> true, () -> {
            var result = SmokeScenario.clientMonitor().result().orElseThrow();
            check(result.irrigable() == 64, "irrigable " + result.irrigable());
            screenshot("monitor_irrigation");
        });
        step("toggle overlay", () -> true, () -> ClientSmoke.press(SmokeScenario.text("gui.homelink_farm.overlay.toggle")));
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
        step("overlay data", () -> IrrigationOverlay.enabled() && IrrigationOverlay.data().sprinklers().stream()
                .anyMatch(mark -> mark.state() == IrrigationVisual.ACTIVE), () -> check(!IrrigationOverlay.data().uncovered().isEmpty(),
                "overlay should show uncovered young crops"));
        pause(20);
        step("overlay screenshot", () -> true, () -> screenshot("overlay"));
        step("overlay off", () -> true, IrrigationOverlay::toggle);
        HomeCoreSmoke.define();
    }

    /** Places a block with the state a player placement would give (pipe connections included). */
    static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlockAndUpdate(pos, Block.updateFromNeighbourShapes(block.defaultBlockState(), level, pos));
    }

    static void openPump() {
        step("open pump", () -> true, () -> onServer(player -> player.openMenu((IrrigationPumpBlockEntity) player.level().getBlockEntity(PUMP), PUMP)));
        step("pump screen", () -> Minecraft.getInstance().screen instanceof IrrigationPumpScreen, () -> { });
        pause(10);
    }

    static IrrigationPumpBlockEntity clientPump() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(PUMP) instanceof IrrigationPumpBlockEntity pump ? pump : null;
    }

    static PumpStatus pumpStatus() {
        return clientPump() == null ? null : clientPump().snapshot().status();
    }

    static boolean allSprinklers(IrrigationVisual expected) {
        var level = Minecraft.getInstance().level;
        for (int x : SPRINKLER_X) {
            var state = level.getBlockState(new BlockPos(x, GROUND + 2, 12));
            if (!state.hasProperty(IrrigationVisual.PROPERTY) || state.getValue(IrrigationVisual.PROPERTY) != expected) return false;
        }
        return true;
    }
}
