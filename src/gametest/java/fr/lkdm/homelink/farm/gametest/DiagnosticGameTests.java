package fr.lkdm.homelink.farm.gametest;

import static fr.lkdm.homelink.farm.gametest.CropMonitorGameTests.monitor;
import static fr.lkdm.homelink.farm.gametest.CropMonitorGameTests.wheat;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.network.LocateProblemPayload;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 3: reliable diagnostics, locate validation and problem aggregation. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DiagnosticGameTests {
    private DiagnosticGameTests() {
    }

    static void farmland(GameTestHelper helper, int x, int z, int moisture) {
        helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, moisture));
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void dryFarmlandIsReportedForGrowingCropsOnly(GameTestHelper helper) {
        farmland(helper, 2, 2, 0);
        helper.setBlock(new BlockPos(2, 2, 2), wheat(2));
        farmland(helper, 3, 2, 7);
        helper.setBlock(new BlockPos(3, 2, 2), wheat(2));
        farmland(helper, 4, 2, 0);
        helper.setBlock(new BlockPos(4, 2, 2), wheat(7));
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(4, 2, 2))));
        BlockPos dry = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.succeedWhen(() -> {
            CropScanResult result = monitor.result().orElse(null);
            helper.assertTrue(result != null, "No result yet");
            helper.assertTrue(result.problems().get(ProblemType.DRY_FARMLAND) == 1,
                    "Expected exactly 1 dry farmland problem, got " + result.problems());
            helper.assertTrue(result.problems().get(ProblemType.LOW_LIGHT) == 0, "Unexpected light problem in daylight");
            helper.assertTrue(result.samples().size() == 1 && result.samples().getFirst().pos().equals(dry), "Wrong problem location");
            helper.assertTrue(monitor.findProblem(dry).isPresent(), "findProblem missed a reported problem");
        });
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void darknessIsReportedAsLowLight(GameTestHelper helper) {
        // Wheat enclosed in stone: no sky light and no block light reaches it.
        for (int x = 1; x <= 3; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
            }
        }
        farmland(helper, 2, 2, 7);
        helper.setBlock(new BlockPos(2, 2, 2), wheat(1));
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(6, 2, 6));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(2, 2, 2))));
        helper.succeedWhen(() -> {
            monitor.scanner().requestPass();
            CropScanResult result = monitor.result().orElse(null);
            helper.assertTrue(result != null && result.crops() == 1, "Enclosed crop not scanned");
            helper.assertTrue(result.problems().get(ProblemType.LOW_LIGHT) == 1, "Darkness not reported: " + result.problems());
        });
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void locateRequiresOpenScreenAndRealProblem(GameTestHelper helper) {
        farmland(helper, 2, 2, 0);
        helper.setBlock(new BlockPos(2, 2, 2), wheat(2));
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(2, 2, 2))));
        BlockPos monitorPos = helper.absolutePos(new BlockPos(0, 2, 0));
        BlockPos problem = helper.absolutePos(new BlockPos(2, 2, 2));
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "locate_tester"));
        helper.succeedWhen(() -> {
            helper.assertTrue(monitor.findProblem(problem).isPresent(), "Problem not detected yet");
            // No screen open: the request must be refused even for a real problem.
            helper.assertTrue(LocateProblemPayload.authorize(player, monitorPos, problem).isEmpty(), "Locate allowed without an open screen");
            helper.assertTrue(monitor.findProblem(problem.above(5)).isEmpty(), "Arbitrary position accepted as a problem");
        });
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void controllerSumsProblems(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) {
            farmland(helper, x, 2, 0);
            helper.setBlock(new BlockPos(x, 2, 2), wheat(1));
        }
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(4, 2, 2))));
        helper.setBlock(new BlockPos(8, 2, 8), ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(8, 2, 8));
        FarmLinkService.link(helper.getLevel(), controller, monitor, 32);
        helper.succeedWhen(() -> {
            helper.assertTrue(monitor.result().isPresent(), "No result yet");
            controller.refreshSummary(helper.getLevel());
            helper.assertTrue(controller.summary().problems().get(ProblemType.DRY_FARMLAND) == 3,
                    "Controller problems: " + controller.summary().problems());
        });
    }
}
