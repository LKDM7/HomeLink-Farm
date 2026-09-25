package fr.lkdm.homelink.farm.gametest;

import static fr.lkdm.homelink.farm.gametest.CropMonitorGameTests.monitor;
import static fr.lkdm.homelink.farm.gametest.IrrigationGrowthGameTests.plant;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.PUMP;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.buildLine;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.pump;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.water;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmComponentKind;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.controller.LinkedComponent;
import fr.lkdm.homelink.farm.farm.crop.ComparatorMode;
import fr.lkdm.homelink.farm.farm.crop.CropInspector;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.crop.CropScanner;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.farm.irrigation.RedstoneMode;
import fr.lkdm.homelink.farm.network.DeviceCommandPayload;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 7: redstone, unloaded data, security, several farms and performance. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RobustnessGameTests {
    private RobustnessGameTests() {
    }

    static int analog(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return helper.getLevel().getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), pos);
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void monitorComparatorFollowsItsMode(GameTestHelper helper) {
        plant(helper, 2, 5, 2, 2, 7);   // 4 mature
        plant(helper, 2, 5, 3, 3, 0);   // 4 seeds
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(5, 2, 3))));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(monitor.result().isPresent(), "No result"))
                .thenExecute(() -> {
                    // Maturity 50% -> round(7.5) = 8; ready 50% -> 8.
                    helper.assertTrue(analog(helper, new BlockPos(0, 2, 0)) == 8, "MATURITY signal " + analog(helper, new BlockPos(0, 2, 0)));
                    monitor.setComparatorMode(ComparatorMode.PROBLEMS);
                    helper.assertTrue(analog(helper, new BlockPos(0, 2, 0)) == Math.min(15, monitor.result().get().problems().total()), "PROBLEMS signal");
                    monitor.setComparatorMode(ComparatorMode.MATURITY);
                    for (int x = 2; x <= 5; x++) helper.setBlock(new BlockPos(x, 2, 3), CropMonitorGameTests.wheat(7));
                    monitor.scanner().requestPass();
                })
                .thenWaitUntil(() -> helper.assertTrue(analog(helper, new BlockPos(0, 2, 0)) == 15, "Fully mature field should output 15"))
                .thenExecute(() -> {
                    monitor.clearZone();
                    helper.assertTrue(analog(helper, new BlockPos(0, 2, 0)) == 0, "No zone should output 0");
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 400)
    public static void pumpObeysRedstoneAndOutputsItsState(GameTestHelper helper) {
        buildLine(helper, 3, true);
        IrrigationPumpBlockEntity pump = pump(helper, PUMP);
        BlockPos lever = PUMP.north();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.ACTIVE, "Not active"))
                .thenExecute(() -> {
                    helper.assertTrue(analog(helper, PUMP) == 3, "ACTIVE pump with 3 sprinklers should output 3, got " + analog(helper, PUMP));
                    pump.setRedstoneMode(RedstoneMode.RUN_WHEN_POWERED);
                })
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.REDSTONE_STOPPED, "Unpowered pump still runs"))
                .thenExecute(() -> {
                    helper.assertTrue(analog(helper, PUMP) == 0, "Stopped pump should output 0");
                    helper.setBlock(lever, Blocks.REDSTONE_BLOCK);
                })
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.ACTIVE, "Powered pump did not start"))
                .thenExecute(() -> pump.setRedstoneMode(RedstoneMode.STOP_WHEN_POWERED))
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.REDSTONE_STOPPED, "Signal did not stop the pump"))
                .thenExecute(() -> {
                    helper.destroyBlock(lever);
                    pump.setRedstoneMode(RedstoneMode.IGNORED);
                    for (int x = 8; x <= 13; x++) helper.setBlock(new BlockPos(x, 2, 1), ModBlocks.COPPER_PIPE.get());
                    for (int i = 3; i < 6; i++) helper.setBlock(IrrigationNetworkGameTests.sprinkler(i), ModBlocks.COPPER_SPRINKLER.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.OVER_CAPACITY, "Not over capacity"))
                .thenExecute(() -> helper.assertTrue(analog(helper, PUMP) == PumpStatus.FAULT_SIGNAL, "Fault should output 15"))
                .thenSucceed();
    }

    @GameTest(template = "large", timeoutTicks = 400)
    public static void severalFarmsStayIndependent(GameTestHelper helper) {
        int[] sprinklersPerFarm = {2, 5, 6};
        List<FarmControllerBlockEntity> controllers = new ArrayList<>();
        List<IrrigationPumpBlockEntity> pumps = new ArrayList<>();
        for (int farm = 0; farm < 3; farm++) {
            int z = 4 + farm * 20;
            BlockPos pumpPos = new BlockPos(1, 2, z);
            water(helper, pumpPos);
            helper.setBlock(pumpPos, ModBlocks.IRRIGATION_PUMP.get());
            for (int x = 2; x <= 13; x++) helper.setBlock(new BlockPos(x, 2, z), ModBlocks.COPPER_PIPE.get());
            for (int i = 0; i < sprinklersPerFarm[farm]; i++) helper.setBlock(new BlockPos(2 + i * 2, 3, z), ModBlocks.COPPER_SPRINKLER.get());
            plant(helper, 0, 12, z + 1, z + 2, 1);
            CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(20, 2, z));
            monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(0, 2, z + 1)), helper.absolutePos(new BlockPos(12, 2, z + 2))));
            helper.setBlock(new BlockPos(22, 2, z), ModBlocks.FARM_CONTROLLER.get());
            FarmControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(22, 2, z));
            IrrigationPumpBlockEntity pump = helper.getBlockEntity(pumpPos);
            FarmLinkService.link(helper.getLevel(), controller, monitor, 32);
            FarmLinkService.link(helper.getLevel(), controller, pump, 32);
            controllers.add(controller);
            pumps.add(pump);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(pumps.get(0).snapshot().status() == PumpStatus.ACTIVE, "Farm 1 pump " + pumps.get(0).snapshot().status());
            helper.assertTrue(pumps.get(1).snapshot().status() == PumpStatus.ACTIVE, "Farm 2 pump " + pumps.get(1).snapshot().status());
            helper.assertTrue(pumps.get(2).snapshot().status() == PumpStatus.OVER_CAPACITY, "Farm 3 pump " + pumps.get(2).snapshot().status());
            for (int farm = 0; farm < 3; farm++) {
                FarmControllerBlockEntity controller = controllers.get(farm);
                controller.refreshSummary(helper.getLevel());
                var summary = controller.summary();
                helper.assertTrue(summary.sprinklers() == sprinklersPerFarm[farm], "Farm " + (farm + 1) + " sprinklers " + summary.sprinklers());
                helper.assertTrue(summary.crops() == 26, "Farm " + (farm + 1) + " crops " + summary.crops());
                helper.assertTrue(summary.pumpFaults() == (farm == 2 ? 1 : 0), "Farm " + (farm + 1) + " faults " + summary.pumpFaults());
            }
            helper.assertTrue(controllers.get(2).summary().irrigated() == 0, "Overloaded farm must not irrigate");
            helper.assertTrue(controllers.get(0).summary().irrigated() > 0, "Healthy farm irrigates");
        });
    }

    @GameTest(template = "empty")
    public static void unloadedComponentsUseLastKnownFigures(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 1, 1), ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(1, 1, 1));
        UUID monitorId = UUID.randomUUID();
        BlockPos farAway = new BlockPos(29_000_000, 64, 29_000_016);
        helper.assertFalse(helper.getLevel().hasChunk(farAway.getX() >> 4, farAway.getZ() >> 4), "Chunk unexpectedly loaded");
        controller.linkedComponents().add(new LinkedComponent(monitorId, farAway, FarmComponentKind.CROP_MONITOR), 32);
        controller.refreshSummary(helper.getLevel());
        helper.assertTrue(controller.summary().unavailable() == 1 && controller.summary().stale() == 0, "Unknown unloaded monitor must be unavailable");
        controller.lastKnown().remember(monitorId, new CropScanResult(40, 30, 0.8F, 0, 0));
        controller.refreshSummary(helper.getLevel());
        helper.assertTrue(controller.summary().stale() == 1 && controller.summary().crops() == 40, "Last known figures not used");
        helper.assertFalse(helper.getLevel().hasChunk(farAway.getX() >> 4, farAway.getZ() >> 4), "Aggregation loaded a chunk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void commandsRequireOpenScreenAndRights(GameTestHelper helper) {
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(1, 1, 1));
        var stranger = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "stranger"));
        monitor.setOwner(UUID.randomUUID(), "owner");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(DeviceCommandPayload.authorize(stranger, pos, true) == DeviceCommandPayload.Decision.NO_OPEN_SCREEN,
                "Command accepted without the device screen open");
        helper.assertTrue(DeviceCommandPayload.authorize(stranger, pos, false) == DeviceCommandPayload.Decision.NO_OPEN_SCREEN,
                "View command accepted without the device screen open");
        helper.assertFalse(fr.lkdm.homelink.farm.farm.FarmAccess.canManage(stranger, monitor), "Stranger may manage another player's monitor");
        helper.succeed();
    }

    @GameTest(template = "large", timeoutTicks = 600)
    public static void maximumZoneScanStaysCheap(GameTestHelper helper) {
        // Default maximum volume: 32 x 32 x 32 = 32768 positions, with a 32 x 32 wheat field.
        for (int x = 0; x < 32; x++) {
            for (int z = 0; z < 32; z++) {
                helper.setBlock(new BlockPos(x + 1, 1, z + 1), Blocks.FARMLAND);
                helper.setBlock(new BlockPos(x + 1, 2, z + 1), CropMonitorGameTests.wheat((x + z) % 8));
            }
        }
        CropZone zone = new CropZone(helper.absolutePos(new BlockPos(1, 0, 1)), helper.absolutePos(new BlockPos(32, 31, 32)));
        helper.assertTrue(zone.volume() == 32768, "zone volume " + zone.volume());
        CropScanner scanner = new CropScanner();
        scanner.setZone(zone);
        // Pass 1 warms the JIT up; pass 2 is measured, so a one-off compilation pause is not reported as a slow scan.
        long[] stats = new long[3];
        boolean[] measuring = {false};
        helper.succeedWhen(() -> {
            long start = System.nanoTime();
            CropScanResult result = scanner.tick(helper.getLevel(), CropInspector.INSTANCE);
            long nanos = System.nanoTime() - start;
            stats[0] = Math.max(stats[0], nanos);
            stats[1] += nanos;
            stats[2]++;
            if (result != null && !measuring[0]) {
                measuring[0] = true;
                java.util.Arrays.fill(stats, 0);
                scanner.requestPass();
            }
            helper.assertTrue(result != null && measuring[0] && stats[2] > 0, "Measured scan in progress (" + stats[2] + " ticks)");
            helper.assertTrue(result.crops() == 1024, "Expected 1024 crops, got " + result.crops());
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PERF max_zone=32768 ticks={} avg_tick_ms={} worst_tick_ms={} (after JIT warm-up)",
                    stats[2], stats[1] / 1e6 / stats[2], stats[0] / 1e6);
            helper.assertTrue(stats[2] >= 32768 / 512, "Scan not budgeted: " + stats[2] + " ticks");
            helper.assertTrue(stats[0] < 10_000_000L, "A scan tick took " + stats[0] / 1e6 + " ms");
        });
    }

    @GameTest(template = "empty")
    public static void globalBudgetCapsManyMonitors(GameTestHelper helper) {
        List<CropScanner> scanners = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            CropScanner scanner = new CropScanner();
            BlockPos corner = helper.absolutePos(new BlockPos(0, 1, 0)).offset(i * 3, 0, 0);
            scanner.setZone(new CropZone(corner, corner.offset(15, 15, 15)));
            scanners.add(scanner);
        }
        // All in the same server tick: the shared budget (8192 by default) is split between them.
        for (CropScanner scanner : scanners) scanner.tick(helper.getLevel(), CropInspector.INSTANCE);
        double examined = scanners.stream().mapToDouble(scanner -> scanner.progress() * 4096).sum();
        HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PERF monitors=40 positions_examined_in_one_tick={}", Math.round(examined));
        helper.assertTrue(examined <= fr.lkdm.homelink.farm.config.FarmServerConfig.GLOBAL_SCAN_BUDGET_PER_TICK.get() + 1,
                "Global scan budget exceeded: " + examined);
        helper.succeed();
    }
}
