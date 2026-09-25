package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.block.IrrigationPumpBlock;
import fr.lkdm.homelink.farm.block.pipe.CopperPipeBlock;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationNetwork;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WeatheringCopper;
import net.neoforged.neoforge.common.DataMapHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 4: copper network discovery, invalidation, water, and the 5-sprinklers-per-pump rule. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class IrrigationNetworkGameTests {
    static final BlockPos PUMP = new BlockPos(1, 2, 1);

    private IrrigationNetworkGameTests() {
    }

    /** Water source under {@code pumpPos}, contained by stone so it cannot spread. */
    static void water(GameTestHelper helper, BlockPos pumpPos) {
        BlockPos source = pumpPos.below();
        helper.setBlock(source.below(), Blocks.STONE);
        for (var direction : net.minecraft.core.Direction.Plane.HORIZONTAL) helper.setBlock(source.relative(direction), Blocks.STONE);
        helper.setBlock(source, Blocks.WATER);
    }

    /** Pump at {@link #PUMP} with water, a pipe line eastwards and {@code sprinklers} sprinklers on it. */
    static void buildLine(GameTestHelper helper, int sprinklers, boolean withWater) {
        if (withWater) water(helper, PUMP);
        helper.setBlock(PUMP, ModBlocks.IRRIGATION_PUMP.get());
        int length = Math.max(2, sprinklers * 2);
        for (int x = 2; x <= 1 + length; x++) helper.setBlock(new BlockPos(x, 2, 1), ModBlocks.COPPER_PIPE.get());
        for (int i = 0; i < sprinklers; i++) helper.setBlock(sprinkler(i), ModBlocks.COPPER_SPRINKLER.get());
    }

    static BlockPos sprinkler(int index) {
        return new BlockPos(2 + index * 2, 3, 1);
    }

    static IrrigationVisual visual(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(IrrigationVisual.PROPERTY);
    }

    static IrrigationPumpBlockEntity pump(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos);
    }

    static void assertSprinklers(GameTestHelper helper, int count, IrrigationVisual expected) {
        for (int i = 0; i < count; i++) {
            helper.assertTrue(visual(helper, sprinkler(i)) == expected, "Sprinkler #" + (i + 1) + " is " + visual(helper, sprinkler(i)) + ", expected " + expected);
        }
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void oneSprinklerIsActive(GameTestHelper helper) {
        buildLine(helper, 1, true);
        helper.succeedWhen(() -> {
            var snapshot = pump(helper, PUMP).snapshot();
            helper.assertTrue(snapshot.status() == PumpStatus.ACTIVE, "Pump status " + snapshot.status());
            helper.assertTrue(snapshot.sprinklers() == 1 && snapshot.capacity() == 5, "Expected 1 / 5, got " + snapshot.sprinklers() + " / " + snapshot.capacity());
            assertSprinklers(helper, 1, IrrigationVisual.ACTIVE);
        });
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void fiveSprinklersAreActive(GameTestHelper helper) {
        buildLine(helper, 5, true);
        helper.succeedWhen(() -> {
            var snapshot = pump(helper, PUMP).snapshot();
            helper.assertTrue(snapshot.status() == PumpStatus.ACTIVE, "Pump status " + snapshot.status());
            helper.assertTrue(snapshot.sprinklers() == 5, "Expected 5 sprinklers, got " + snapshot.sprinklers());
            assertSprinklers(helper, 5, IrrigationVisual.ACTIVE);
        });
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void sixSprinklersOverloadTheWholeNetwork(GameTestHelper helper) {
        buildLine(helper, 6, true);
        helper.succeedWhen(() -> {
            var snapshot = pump(helper, PUMP).snapshot();
            helper.assertTrue(snapshot.status() == PumpStatus.OVER_CAPACITY, "Pump status " + snapshot.status());
            helper.assertTrue(snapshot.sprinklers() == 6 && snapshot.capacity() == 5, "Expected 6 / 5");
            helper.assertTrue(visual(helper, PUMP) == IrrigationVisual.ERROR, "Pump not shown in error");
            // Deterministic rule: no sprinkler of an overloaded network irrigates.
            assertSprinklers(helper, 6, IrrigationVisual.ERROR);
        });
    }

    @GameTest(template = "field", timeoutTicks = 400)
    public static void brokenPipeDisconnectsThenReconnects(GameTestHelper helper) {
        buildLine(helper, 2, true);
        BlockPos cut = new BlockPos(3, 2, 1);
        helper.startSequence()
                .thenWaitUntil(() -> assertSprinklers(helper, 2, IrrigationVisual.ACTIVE))
                .thenExecute(() -> helper.destroyBlock(cut))
                .thenWaitUntil(() -> {
                    helper.assertTrue(visual(helper, sprinkler(1)) == IrrigationVisual.OFF, "Cut-off sprinkler still " + visual(helper, sprinkler(1)));
                    helper.assertTrue(visual(helper, sprinkler(0)) == IrrigationVisual.ACTIVE, "Connected sprinkler stopped");
                    helper.assertTrue(pump(helper, PUMP).snapshot().sprinklers() == 1, "Pump still counts the cut-off sprinkler");
                })
                .thenExecute(() -> helper.setBlock(cut, ModBlocks.COPPER_PIPE.get()))
                .thenWaitUntil(() -> {
                    assertSprinklers(helper, 2, IrrigationVisual.ACTIVE);
                    helper.assertTrue(pump(helper, PUMP).snapshot().sprinklers() == 2, "Reconnected sprinkler not counted");
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void removedPumpStopsIrrigation(GameTestHelper helper) {
        buildLine(helper, 2, true);
        helper.startSequence()
                .thenWaitUntil(() -> assertSprinklers(helper, 2, IrrigationVisual.ACTIVE))
                .thenExecute(() -> helper.destroyBlock(PUMP))
                .thenWaitUntil(() -> assertSprinklers(helper, 2, IrrigationVisual.OFF))
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void pumpNeedsRealWater(GameTestHelper helper) {
        buildLine(helper, 1, false);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.NO_WATER, "Pump without water: " + pump(helper, PUMP).snapshot().status()))
                .thenExecute(() -> helper.assertTrue(visual(helper, sprinkler(0)) != IrrigationVisual.ACTIVE, "Sprinkler fed without water"))
                .thenExecute(() -> water(helper, PUMP))
                .thenWaitUntil(() -> {
                    helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.ACTIVE, "Pump did not detect the new water source");
                    assertSprinklers(helper, 1, IrrigationVisual.ACTIVE);
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void submergedPumpDrawsFromItsOwnWater(GameTestHelper helper) {
        buildLine(helper, 1, false);
        helper.setBlock(PUMP.below(), Blocks.STONE);
        helper.setBlock(PUMP.west(), Blocks.STONE);
        helper.setBlock(PUMP.north(), Blocks.STONE);
        helper.setBlock(PUMP.south(), Blocks.STONE);
        helper.setBlock(PUMP, ModBlocks.IRRIGATION_PUMP.get().defaultBlockState().setValue(IrrigationPumpBlock.WATERLOGGED, true));
        // Give the pump's water time to flow: it must not wash the adjacent pipe away.
        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(ModBlocks.COPPER_PIPE.get(), PUMP.east());
                    helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(PUMP)).isSource(), "Submerged pump lost its water");
                    helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.ACTIVE, "Submerged pump status " + pump(helper, PUMP).snapshot().status());
                    assertSprinklers(helper, 1, IrrigationVisual.ACTIVE);
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void pipesCanBeLaidUnderwater(GameTestHelper helper) {
        BlockPos pipe = new BlockPos(1, 2, 1);
        helper.setBlock(pipe, Blocks.WATER);
        helper.setBlock(pipe, ModBlocks.COPPER_PIPE.get().defaultBlockState().setValue(CopperPipeBlock.WATERLOGGED, true));
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertBlockPresent(ModBlocks.COPPER_PIPE.get(), pipe);
                    helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(pipe)).isSource(), "Waterlogged pipe lost its water");
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void sprinklersCanBePlacedUnderwater(GameTestHelper helper) {
        buildLine(helper, 1, true);
        BlockPos sprinkler = sprinkler(0);
        // Its own water flows around it: the sprinkler must stay, keep the water and keep irrigating.
        helper.setBlock(sprinkler, helper.getBlockState(sprinkler).setValue(CopperSprinklerBlock.WATERLOGGED, true));
        helper.startSequence()
                .thenIdle(40)
                .thenWaitUntil(() -> {
                    helper.assertBlockPresent(ModBlocks.COPPER_SPRINKLER.get(), sprinkler);
                    helper.assertTrue(helper.getLevel().getFluidState(helper.absolutePos(sprinkler)).isSource(), "Waterlogged sprinkler lost its water");
                    assertSprinklers(helper, 1, IrrigationVisual.ACTIVE);
                    helper.assertTrue(helper.getBlockState(sprinkler).getValue(CopperSprinklerBlock.WATERLOGGED), "Visual update dropped the water");
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void disabledPumpStopsItsNetwork(GameTestHelper helper) {
        buildLine(helper, 1, true);
        helper.startSequence()
                .thenWaitUntil(() -> assertSprinklers(helper, 1, IrrigationVisual.ACTIVE))
                .thenExecute(() -> pump(helper, PUMP).setEnabled(false))
                .thenWaitUntil(() -> {
                    helper.assertTrue(pump(helper, PUMP).snapshot().status() == PumpStatus.DISABLED, "Pump not disabled");
                    assertSprinklers(helper, 1, IrrigationVisual.OFF);
                })
                .thenSucceed();
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void twoPumpsFeedTenSprinklers(GameTestHelper helper) {
        buildLine(helper, 6, true);
        // Second pump with its own water at the other end of the same pipe line.
        BlockPos secondPump = new BlockPos(14, 2, 1);
        water(helper, secondPump);
        helper.setBlock(secondPump, ModBlocks.IRRIGATION_PUMP.get());
        helper.setBlock(new BlockPos(13, 2, 1), ModBlocks.COPPER_PIPE.get());
        helper.succeedWhen(() -> {
            var snapshot = pump(helper, PUMP).snapshot();
            helper.assertTrue(snapshot.pumps() == 2, "Network should contain 2 pumps, got " + snapshot.pumps());
            helper.assertTrue(snapshot.status() == PumpStatus.ACTIVE, "6 sprinklers on 2 pumps should be ACTIVE, got " + snapshot.status());
            helper.assertTrue(snapshot.capacity() == 10, "Capacity should be 10, got " + snapshot.capacity());
            assertSprinklers(helper, 6, IrrigationVisual.ACTIVE);
        });
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void oxidationIsCosmeticOnly(GameTestHelper helper) {
        helper.assertTrue(WeatheringCopper.getNext(ModBlocks.COPPER_PIPE.get()).orElse(null) == ModBlocks.EXPOSED_COPPER_PIPE.get(),
                "Oxidation data map not loaded");
        helper.assertTrue(WeatheringCopper.getPrevious(ModBlocks.OXIDIZED_COPPER_PIPE.get()).orElse(null) == ModBlocks.WEATHERED_COPPER_PIPE.get(),
                "Axe scraping (inverse oxidation) not available");
        Block waxed = DataMapHooks.getBlockWaxed(ModBlocks.WEATHERED_COPPER_PIPE.get());
        helper.assertTrue(waxed == ModBlocks.WAXED_WEATHERED_COPPER_PIPE.get(), "Honeycomb waxing data map not loaded");
        buildLine(helper, 2, true);
        helper.setBlock(new BlockPos(2, 2, 1), ModBlocks.OXIDIZED_COPPER_PIPE.get());
        helper.setBlock(new BlockPos(3, 2, 1), ModBlocks.WAXED_WEATHERED_COPPER_PIPE.get());
        helper.succeedWhen(() -> assertSprinklers(helper, 2, IrrigationVisual.ACTIVE));
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void networkIsCachedUntilStructureChanges(GameTestHelper helper) {
        buildLine(helper, 1, true);
        var manager = IrrigationManager.get(helper.getLevel());
        BlockPos pumpPos = helper.absolutePos(PUMP);
        IrrigationNetwork first = manager.networkForPump(pumpPos);
        helper.assertTrue(manager.networkForPump(pumpPos) == first, "Network rebuilt without any change");
        helper.setBlock(new BlockPos(10, 2, 10), ModBlocks.COPPER_PIPE.get());
        helper.assertTrue(manager.networkForPump(pumpPos) == first, "Unrelated pipe invalidated the network");
        helper.setBlock(new BlockPos(1, 2, 2), ModBlocks.COPPER_PIPE.get());
        IrrigationNetwork rebuilt = manager.networkForPump(pumpPos);
        helper.assertTrue(rebuilt != first && !first.valid(), "Adjacent pipe did not invalidate the network");
        helper.assertTrue(rebuilt.pipes() == first.pipes() + 1, "New pipe not part of the rebuilt network");
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 200)
    public static void hangingSprinklerUnderAPipeWatersCropsBelow(GameTestHelper helper) {
        water(helper, PUMP);
        helper.setBlock(PUMP, ModBlocks.IRRIGATION_PUMP.get());
        for (int y = 3; y <= 6; y++) helper.setBlock(new BlockPos(1, y, 1), ModBlocks.COPPER_PIPE.get());
        for (int x = 2; x <= 6; x++) helper.setBlock(new BlockPos(x, 6, 1), ModBlocks.COPPER_PIPE.get());
        BlockPos hanging = new BlockPos(4, 5, 1);
        helper.setBlock(hanging, ModBlocks.COPPER_SPRINKLER.get().defaultBlockState().setValue(CopperSprinklerBlock.HANGING, true));
        // A STANDING sprinkler under a pipe must not connect through its top.
        helper.setBlock(new BlockPos(6, 5, 1), ModBlocks.COPPER_SPRINKLER.get());
        IrrigationGrowthGameTests.plant(helper, 2, 6, 0, 2, 0);
        BlockPos crop = helper.absolutePos(new BlockPos(4, 2, 1));
        helper.succeedWhen(() -> {
            helper.assertTrue(visual(helper, hanging) == IrrigationVisual.ACTIVE, "Hanging sprinkler not fed from the pipe above");
            helper.assertTrue(pump(helper, PUMP).snapshot().sprinklers() == 1, "Standing sprinkler connected through its top: " + pump(helper, PUMP).snapshot().sprinklers());
            helper.assertTrue(visual(helper, new BlockPos(6, 5, 1)) == IrrigationVisual.OFF, "Standing sprinkler under a pipe is fed");
            // The crop is below the hanging sprinkler, which is supplied through its top.
            helper.assertTrue(IrrigationManager.get(helper.getLevel()).coverage().irrigated(crop), "Crop under the hanging sprinkler not irrigated");
        });
    }
}
