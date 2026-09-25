package fr.lkdm.homelink.farm.gametest;

import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.PUMP;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.assertSprinklers;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.buildLine;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.pump;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.farm.irrigation.GrowthBonus;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationCoverage;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationGrowth;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.neoforged.neoforge.common.FarmlandWaterManager;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Phase 5: coverage, the +20% growth bonus (never stacked), hydration and monitoring.
 * Layout: pump at (1,2,1), pipes along z = 1 at y = 2, sprinklers at y = 3 on x = 2, 4, ...;
 * each sprinkler covers x +-2, z -1..3, y 1..4, so crops at y = 2 in rows z = 2..3 are covered.
 */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IrrigationGrowthGameTests {
    private IrrigationGrowthGameTests() {
    }

    /** Wheat of the given age on moist farmland for x in [x0, x1] and z in [z0, z1]. */
    static int plant(GameTestHelper helper, int x0, int x1, int z0, int z1, int age) {
        int count = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7));
                helper.setBlock(new BlockPos(x, 2, z), ((CropBlock) Blocks.WHEAT).getStateForAge(age));
                count++;
            }
        }
        return count;
    }

    /** Irrigated positions of the real level coverage restricted to this test's area. */
    static LongOpenHashSet irrigatedInTest(GameTestHelper helper) {
        IrrigationCoverage coverage = IrrigationManager.get(helper.getLevel()).coverage();
        LongOpenHashSet result = new LongOpenHashSet();
        for (int x = -3; x <= 17; x++) {
            for (int y = 0; y <= 5; y++) {
                for (int z = -3; z <= 17; z++) {
                    BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                    if (coverage.irrigated(pos)) result.add(pos.asLong());
                }
            }
        }
        return result;
    }

    static double averageAge(GameTestHelper helper, int x0, int x1, int z0, int z1) {
        double sum = 0;
        int count = 0;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                var state = helper.getBlockState(new BlockPos(x, 2, z));
                if (state.getBlock() instanceof CropBlock crop) {
                    sum += crop.getAge(state);
                    count++;
                }
            }
        }
        return count == 0 ? 0 : sum / count;
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void overlappingSprinklersDoNotStack(GameTestHelper helper) {
        buildLine(helper, 2, true);
        int crops = plant(helper, 0, 6, 2, 3, 0);
        helper.succeedWhen(() -> {
            assertSprinklers(helper, 2, IrrigationVisual.ACTIVE);
            LongOpenHashSet irrigated = irrigatedInTest(helper);
            // Sprinklers at y 3 reach 1 above and 12 below, clipped to the scanned layers y 0..4: two
            // 5 x 5 x 5 areas (125 positions each) overlapping on 3 columns, union = 7 x 5 x 5 = 175.
            helper.assertTrue(irrigated.size() == 175, "Coverage union should be 175 positions, got " + irrigated.size());
            // Force exactly one extra tick per covered position: every crop gets ONE tick, not two.
            int delivered = IrrigationGrowth.apply(helper.getLevel(), IrrigationCoverage.ofIrrigated(irrigated), 1.0);
            helper.assertTrue(delivered == crops, "Expected " + crops + " bonus ticks (one per crop), got " + delivered);
        });
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void irrigatedCropsGrowFasterThanControl(GameTestHelper helper) {
        buildLine(helper, 2, true);
        plant(helper, 0, 6, 2, 3, 0);
        plant(helper, 0, 6, 9, 10, 0);
        int randomTickSpeed = helper.getLevel().getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        helper.succeedWhen(() -> {
            assertSprinklers(helper, 2, IrrigationVisual.ACTIVE);
            IrrigationCoverage coverage = IrrigationCoverage.ofIrrigated(irrigatedInTest(helper));
            for (int i = 0; i < 40; i++) IrrigationGrowth.apply(helper.getLevel(), coverage, 1.0);
            double irrigated = averageAge(helper, 0, 6, 2, 3);
            double control = averageAge(helper, 0, 6, 9, 10);
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_GROWTH irrigated_avg_age={} control_avg_age={}", irrigated, control);
            helper.assertTrue(irrigated >= control + 2, "Irrigated " + irrigated + " vs control " + control);
            helper.assertTrue(helper.getLevel().getGameRules().getInt(GameRules.RULE_RANDOMTICKING) == randomTickSpeed,
                    "randomTickSpeed must never be modified");
        });
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void overloadedNetworkGivesNoBonus(GameTestHelper helper) {
        buildLine(helper, 6, true);
        plant(helper, 0, 13, 2, 3, 0);
        helper.succeedWhen(() -> {
            assertSprinklers(helper, 6, IrrigationVisual.ERROR);
            helper.assertTrue(irrigatedInTest(helper).isEmpty(), "Over-capacity network still irrigates");
            IrrigationCoverage coverage = IrrigationManager.get(helper.getLevel()).coverage();
            helper.assertTrue(coverage.offline(helper.absolutePos(new BlockPos(2, 2, 2))), "Crop under a faulty sprinkler not offline");
        });
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void activeSprinklerHydratesFarmland(GameTestHelper helper) {
        buildLine(helper, 1, true);
        BlockPos farmland = helper.absolutePos(new BlockPos(2, 1, 2));
        helper.setBlock(new BlockPos(2, 1, 2), Blocks.FARMLAND);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(FarmlandWaterManager.hasBlockWaterTicket(helper.getLevel(), farmland),
                        "Active sprinkler does not hydrate its area"))
                .thenExecute(() -> pump(helper, PUMP).setEnabled(false))
                .thenWaitUntil(() -> helper.assertFalse(FarmlandWaterManager.hasBlockWaterTicket(helper.getLevel(), farmland),
                        "Stopped sprinkler still hydrates"))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 400)
    public static void monitorClassifiesIrrigation(GameTestHelper helper) {
        buildLine(helper, 1, true);
        int covered = plant(helper, 0, 4, 2, 3, 1);
        int uncovered = plant(helper, 8, 10, 2, 3, 1);
        helper.setBlock(new BlockPos(14, 2, 14), ModBlocks.CROP_MONITOR.get());
        CropMonitorBlockEntity monitor = helper.getBlockEntity(new BlockPos(14, 2, 14));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(0, 2, 2)), helper.absolutePos(new BlockPos(10, 2, 3))));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    monitor.scanner().requestPass();
                    CropScanResult result = monitor.result().orElse(null);
                    helper.assertTrue(result != null && result.irrigated() == covered,
                            "Irrigated " + (result == null ? "?" : result.irrigated()) + " != " + covered);
                    helper.assertTrue(result.irrigable() == covered + uncovered, "Irrigable count wrong");
                    helper.assertTrue(result.problems().get(ProblemType.NOT_IRRIGATED) == uncovered,
                            "NOT_IRRIGATED problems " + result.problems());
                })
                .thenExecute(() -> pump(helper, PUMP).setEnabled(false))
                .thenWaitUntil(() -> {
                    monitor.scanner().requestPass();
                    CropScanResult result = monitor.result().orElseThrow();
                    helper.assertTrue(result.irrigated() == 0 && result.irrigationOffline() == covered,
                            "Offline irrigation not reported: " + result.irrigationOffline());
                    helper.assertTrue(result.problems().get(ProblemType.IRRIGATION_OFFLINE) == covered, "IRRIGATION_OFFLINE problems");
                })
                .thenSucceed();
    }

    @GameTest(template = "large", timeoutTicks = 300)
    public static void irrigationPerformance(GameTestHelper helper) {
        // 8 independent pumps x 5 sprinklers = 40 sprinklers over wheat.
        for (int line = 0; line < 8; line++) {
            int z = 2 + line * 8;
            BlockPos pumpPos = new BlockPos(1, 2, z);
            IrrigationNetworkGameTests.water(helper, pumpPos);
            helper.setBlock(pumpPos, ModBlocks.IRRIGATION_PUMP.get());
            for (int x = 2; x <= 11; x++) helper.setBlock(new BlockPos(x, 2, z), ModBlocks.COPPER_PIPE.get());
            for (int i = 0; i < 5; i++) helper.setBlock(new BlockPos(2 + i * 2, 3, z), ModBlocks.COPPER_SPRINKLER.get());
            plant(helper, 0, 13, z + 1, z + 2, 0);
        }
        var level = helper.getLevel();
        helper.succeedWhen(() -> {
            var manager = IrrigationManager.get(level);
            long start = System.nanoTime();
            manager.invalidateAround(helper.absolutePos(new BlockPos(60, 2, 60)));
            IrrigationCoverage coverage = manager.coverage();
            long coverageNanos = System.nanoTime() - start;
            helper.assertTrue(coverage.irrigatedSize() >= 40 * 80, "Not all 40 sprinklers irrigate yet: " + coverage.irrigatedSize());
            double expected = GrowthBonus.expectedExtraTicks(0.20, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING), IrrigationGrowth.INTERVAL);
            start = System.nanoTime();
            IrrigationGrowth.apply(level, coverage, expected);
            long growthNanos = System.nanoTime() - start;
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PERF sprinklers>=40 coverage_positions={} coverage_rebuild_ms={} growth_pass_ms={} (once per {} ticks)",
                    coverage.irrigatedSize(), coverageNanos / 1e6, growthNanos / 1e6, IrrigationGrowth.INTERVAL);
            helper.assertTrue(coverageNanos < 50_000_000L && growthNanos < 50_000_000L, "Irrigation work too slow");
        });
    }
}
