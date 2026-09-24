package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.press;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.client.ClientFarmData;
import fr.lkdm.homelink.farm.client.rendering.LocateMarkers;
import fr.lkdm.homelink.farm.client.screen.CropMonitorScreen;
import fr.lkdm.homelink.farm.client.screen.FarmControllerScreen;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;

/** The in-game scenario played by {@link ClientSmoke}; extended phase by phase. */
final class SmokeScenario {
    /** Flat world: grass top at y = -61, so crops stand at y = -60. */
    static final int GROUND = -61;
    static final BlockPos MONITOR = new BlockPos(1, GROUND + 1, 1);
    static final BlockPos CONTROLLER = new BlockPos(4, GROUND + 1, 0);
    /** Young wheat on dry farmland: the two expected diagnostic problems. */
    static final BlockPos DRY_A = new BlockPos(7, GROUND + 1, 4);
    static final BlockPos DRY_B = new BlockPos(8, GROUND + 1, 6);

    private SmokeScenario() {
    }

    static void define() {
        step("build farm", () -> true, () -> onServer(player -> {
            ServerLevel level = player.serverLevel();
            buildField(level);
            level.setBlockAndUpdate(MONITOR, ModBlocks.CROP_MONITOR.get().defaultBlockState());
            level.setBlockAndUpdate(CONTROLLER, ModBlocks.FARM_CONTROLLER.get().defaultBlockState());
            var monitor = (CropMonitorBlockEntity) level.getBlockEntity(MONITOR);
            var controller = (FarmControllerBlockEntity) level.getBlockEntity(CONTROLLER);
            monitor.setOwner(player.getUUID(), player.getGameProfile().getName());
            controller.setOwner(player.getUUID(), player.getGameProfile().getName());
            monitor.setCustomName("Wheat Field");
            controller.setCustomName("Main Farm");
            check(monitor.setZone(new CropZone(new BlockPos(2, GROUND + 1, 2), new BlockPos(9, GROUND + 1, 9))).name().equals("OK"), "zone");
            FarmLinkService.link(level, controller, monitor, 32);
            lookAtField(player);
        }));
        pause(20);
        step("wait scan", () -> clientMonitor() != null && clientMonitor().result().isPresent(), () -> { });
        pause(10);
        step("field screenshot", () -> true, () -> screenshot("field"));
        openMonitor();
        step("monitor screenshot", () -> true, () -> {
            var result = clientMonitor().result().orElseThrow();
            check(result.crops() == 64, "client sees " + result.crops() + " crops, expected 64");
            check(result.ready() == 32, "client sees " + result.ready() + " ready, expected 32");
            check(result.problems().total() == 2, "client sees " + result.problems().total() + " problems, expected 2");
            screenshot("monitor");
        });
        // Phase 3: diagnostic view and LOCATE through the real buttons.
        step("open diagnostic", () -> true, () -> press(text("gui.homelink_farm.diagnostic.open")));
        step("problem list received", () -> ClientFarmData.problems(MONITOR).size() == 2, () -> { });
        pause(25);
        step("diagnostic screenshot", () -> true, () -> screenshot("diagnostic"));
        step("click locate", () -> true, () -> press(text("problem.homelink_farm.dry_farmland")));
        step("marker shown", () -> !LocateMarkers.active().isEmpty(), () -> {
            check(Minecraft.getInstance().screen == null, "Screen should close after LOCATE");
            check(LocateMarkers.active().contains(DRY_A) || LocateMarkers.active().contains(DRY_B), "Marker not on a dry crop");
        });
        pause(20);
        step("locate screenshot", () -> true, () -> screenshot("locate"));
        step("aggregate", () -> true, () -> onServer(player ->
                ((FarmControllerBlockEntity) player.level().getBlockEntity(CONTROLLER)).refreshSummary(player.serverLevel())));
        pause(5);
        step("open controller", () -> true, () -> onServer(player -> player.openMenu((FarmControllerBlockEntity) player.level().getBlockEntity(CONTROLLER), CONTROLLER)));
        step("controller screen", () -> Minecraft.getInstance().screen instanceof FarmControllerScreen
                && clientController() != null && clientController().summary().crops() == 64, () -> { });
        pause(10);
        step("controller screenshot", () -> true, () -> {
            check(clientController().summary().problems().total() == 2, "controller problems");
            screenshot("controller");
        });
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
        ShowcaseSmoke.define();
        IrrigationSmoke.define();
    }

    static void openMonitor() {
        step("open monitor", () -> true, () -> onServer(player -> player.openMenu((CropMonitorBlockEntity) player.level().getBlockEntity(MONITOR), MONITOR)));
        step("monitor screen", () -> Minecraft.getInstance().screen instanceof CropMonitorScreen, () -> { });
        pause(10);
    }

    /** Hovers (creative flight) south of the field, looking down at it. */
    static void lookAtField(ServerPlayer player) {
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), 4.5, GROUND + 6, -2.5, 0, 40);
    }

    static String text(String key) {
        return net.minecraft.network.chat.Component.translatable(key).getString();
    }

    /** 8 x 8 wheat field on moist farmland: left half grown, right half young; two dry spots. */
    static void buildField(ServerLevel level) {
        for (int x = 2; x <= 9; x++) {
            for (int z = 2; z <= 9; z++) {
                BlockPos crop = new BlockPos(x, GROUND + 1, z);
                int moisture = crop.equals(DRY_A) || crop.equals(DRY_B) ? 0 : 7;
                level.setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
                level.setBlockAndUpdate(crop, ((CropBlock) Blocks.WHEAT).getStateForAge(x <= 5 ? 7 : 2));
            }
        }
    }

    static CropMonitorBlockEntity clientMonitor() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(MONITOR) instanceof CropMonitorBlockEntity monitor ? monitor : null;
    }

    static FarmControllerBlockEntity clientController() {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(CONTROLLER) instanceof FarmControllerBlockEntity controller ? controller : null;
    }
}
