package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homecore.api.energy.EnergyApi;
import fr.lkdm.homecore.api.energy.EnergyPort;
import fr.lkdm.homecore.api.energy.EnergyRole;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.PUMP;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.buildLine;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.pump;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.sprinkler;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.visual;

/**
 * Machines need HomeLink Energy. The other batches run with free machines ({@link FreePower});
 * this batch restores the default costs, then frees the machines again for whatever follows.
 */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnergyGameTests {
    private static final String BATCH = "energy";

    private EnergyGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void restoreCosts(ServerLevel level) {
        FreePower.restoreDefaults();
    }

    @AfterBatch(batch = BATCH)
    public static void freeAgain(ServerLevel level) {
        FreePower.enable();
    }

    private static EnergyPort port(GameTestHelper helper, BlockPos pos, Direction side) {
        return helper.getLevel().getCapability(EnergyApi.BLOCK, helper.absolutePos(pos), side);
    }

    @GameTest(template = "field", batch = BATCH, timeoutTicks = 200)
    public static void everyMachineTakesEnergyOnEveryFace(GameTestHelper helper) {
        BlockPos[] machines = {new BlockPos(1, 2, 1), new BlockPos(3, 2, 1), new BlockPos(5, 2, 1)};
        Block[] blocks = {ModBlocks.IRRIGATION_PUMP.get(), ModBlocks.CROP_MONITOR.get(), ModBlocks.FARM_CONTROLLER.get()};
        for (int i = 0; i < machines.length; i++) helper.setBlock(machines[i], blocks[i]);
        helper.succeedWhen(() -> {
            for (int i = 0; i < machines.length; i++) {
                for (Direction side : Direction.values()) {
                    EnergyPort port = port(helper, machines[i], side);
                    helper.assertTrue(port != null, blocks[i] + " has no energy port on " + side);
                    helper.assertTrue(port.role() == EnergyRole.CONSUMER && port.type().canReceive() && !port.type().canSend(),
                            blocks[i] + " is not an input-only consumer");
                }
            }
        });
    }

    @GameTest(template = "field", batch = BATCH, timeoutTicks = 300)
    public static void pumpWaitsForEnergyThenIrrigates(GameTestHelper helper) {
        buildLine(helper, 1, true);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.NO_POWER, "Unpowered pump is " + pump(helper, PUMP).snapshot().status());
                    helper.assertTrue(visual(helper, sprinkler(0)) != IrrigationVisual.ACTIVE, "Sprinkler irrigates without energy");
                })
                .thenExecute(() -> {
                    EnergyPort port = port(helper, PUMP, Direction.NORTH);
                    helper.assertTrue(port.insert(port.requested(), false) > 0, "Pump refused energy");
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.ACTIVE, "Powered pump is " + pump(helper, PUMP).snapshot().status());
                    helper.assertTrue(visual(helper, sprinkler(0)) == IrrigationVisual.ACTIVE, "Sprinkler not irrigating");
                })
                .thenSucceed();
    }

    @GameTest(template = "field", batch = BATCH, timeoutTicks = 400)
    public static void pumpStopsWhenItsEnergyRunsOut(GameTestHelper helper) {
        buildLine(helper, 1, true);
        helper.startSequence()
                .thenExecute(() -> port(helper, PUMP, Direction.UP).insert(2, false))
                .thenWaitUntil(() -> helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.ACTIVE, "Not active"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.NO_POWER, "Still " + pump(helper, PUMP).snapshot().status());
                    helper.assertTrue(port(helper, PUMP, Direction.UP).stored() == 0, "Energy left in the pump");
                    helper.assertTrue(visual(helper, sprinkler(0)) != IrrigationVisual.ACTIVE, "Sprinkler still irrigating");
                })
                .thenSucceed();
    }

    @GameTest(template = "field", batch = BATCH, timeoutTicks = 100)
    public static void defaultCostsAreActive(GameTestHelper helper) {
        helper.assertTrue(FarmServerConfig.PUMP_ENERGY.get() > 0 && FarmServerConfig.CROP_MONITOR_ENERGY.get() > 0
                && FarmServerConfig.CONTROLLER_ENERGY.get() > 0 && FarmServerConfig.FARMBOT_STATION_ENERGY.get() > 0,
                "Energy costs are disabled in the energy batch");
        helper.succeed();
    }
}
