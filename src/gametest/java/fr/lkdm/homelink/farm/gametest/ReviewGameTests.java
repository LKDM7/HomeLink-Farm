package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homelink.farm.farm.crop.CropInspector;
import fr.lkdm.homelink.farm.farm.crop.CropScanner;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Regression coverage for scan accounting and pipe-only chunk lifecycle notifications. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ReviewGameTests {
    private ReviewGameTests() { }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void overlayUsesCropDistanceAndDeduplicatesMonitors(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 1), fr.lkdm.homelink.farm.registry.ModBlocks.COPPER_PIPE.get());
        helper.setBlock(new BlockPos(2, 3, 1), fr.lkdm.homelink.farm.registry.ModBlocks.COPPER_SPRINKLER.get());
        for (int z = 1; z <= 2; z++) {
            CropMonitorGameTests.crop(helper, 3, z, net.minecraft.world.level.block.Blocks.FARMLAND,
                    CropMonitorGameTests.wheat(1));
        }
        BlockPos near = helper.absolutePos(new BlockPos(3, 2, 2));
        BlockPos far = helper.absolutePos(new BlockPos(3, 2, 1));
        var first = CropMonitorGameTests.monitor(helper, new BlockPos(0, 2, 0));
        var second = CropMonitorGameTests.monitor(helper, new BlockPos(1, 2, 0));
        first.setZone(new CropZone(far, near));
        second.setZone(new CropZone(far, near));
        helper.succeedWhen(() -> {
            helper.assertTrue(first.findProblem(near).isPresent() && second.findProblem(near).isPresent(),
                    "Waiting for both monitors");
            var overlay = fr.lkdm.homelink.farm.network.IrrigationOverlayPayloads.collect(helper.getLevel(), near.south(64));
            // Other GameTests share the level and may contribute their own nearby crops.
            helper.assertTrue(overlay.uncovered().stream().filter(problem -> problem.pos().equals(near)).count() == 1,
                    "Nearby crop missing or duplicated");
            helper.assertTrue(overlay.uncovered().stream().noneMatch(problem -> problem.pos().equals(far)), "Out-of-range crop shown");
        });
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void smallZonesOnlySpendTheirActualVolume(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        helper.succeedWhen(() -> {
            // Before the fix, 20 one-block scans spent 20 * 512 positions and
            // could never finish together under the default 8192-position cap.
            for (int i = 0; i < 20; i++) {
                CropScanner scanner = new CropScanner();
                scanner.setZone(new CropZone(pos, pos));
                helper.assertTrue(scanner.tick(helper.getLevel(), CropInspector.INSTANCE) != null,
                        "One-block scan exhausted the global budget at index " + i);
            }
        });
    }

    @GameTest(template = "field")
    public static void chunkLifecycleInvalidatesCachedNetworks(GameTestHelper helper) {
        IrrigationNetworkGameTests.buildLine(helper, 1, true);
        var level = helper.getLevel();
        var manager = IrrigationManager.get(level);
        BlockPos pump = helper.absolutePos(IrrigationNetworkGameTests.PUMP);
        var network = manager.networkForPump(pump);
        var chunk = level.getChunkAt(pump);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Unload(chunk));
        helper.assertTrue(!network.valid(), "Chunk unload retained cached pipe connectivity");
        network = manager.networkForPump(pump);
        NeoForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
        helper.assertTrue(!network.valid(), "Chunk load retained cached pipe connectivity");
        helper.assertTrue(manager.networkForPump(pump).sprinklers().size() == 1, "Network did not rebuild");
        helper.succeed();
    }

    @GameTest(template = "field")
    public static void unrelatedChunksKeepCachedNetworks(GameTestHelper helper) {
        IrrigationNetworkGameTests.buildLine(helper, 1, true);
        var level = helper.getLevel();
        var manager = IrrigationManager.get(level);
        BlockPos pump = helper.absolutePos(IrrigationNetworkGameTests.PUMP);
        var network = manager.networkForPump(pump);
        ChunkPos chunk = new ChunkPos(pump);
        IrrigationManager.chunkChanged(level, new ChunkPos(chunk.x + 10, chunk.z + 10));
        helper.assertTrue(manager.networkForPump(pump) == network, "Unrelated chunk discarded the network cache");
        helper.succeed();
    }
}
