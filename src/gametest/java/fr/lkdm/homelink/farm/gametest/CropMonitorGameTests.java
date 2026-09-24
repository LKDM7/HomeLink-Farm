package fr.lkdm.homelink.farm.gametest;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.CropInspector;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.crop.CropScanner;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.crop.ZoneValidation;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import fr.lkdm.homelink.farm.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 2: crop detection, maturity, budgeted scanning, zones, persistence and aggregation. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CropMonitorGameTests {
    private CropMonitorGameTests() {
    }

    static void crop(GameTestHelper helper, int x, int z, Block soil, BlockState crop) {
        helper.setBlock(new BlockPos(x, 1, z), soil);
        helper.setBlock(new BlockPos(x, 2, z), crop);
    }

    static BlockState wheat(int age) {
        return ((CropBlock) Blocks.WHEAT).getStateForAge(age);
    }

    static CropMonitorBlockEntity monitor(GameTestHelper helper, BlockPos relative) {
        helper.setBlock(relative, ModBlocks.CROP_MONITOR.get());
        return helper.getBlockEntity(relative);
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void scanCountsVanillaCrops(GameTestHelper helper) {
        crop(helper, 2, 2, Blocks.FARMLAND, wheat(7));
        crop(helper, 3, 2, Blocks.FARMLAND, wheat(7));
        crop(helper, 4, 2, Blocks.FARMLAND, wheat(7));
        crop(helper, 2, 3, Blocks.FARMLAND, wheat(3));
        crop(helper, 3, 3, Blocks.FARMLAND, wheat(3));
        crop(helper, 4, 3, Blocks.FARMLAND, ((CropBlock) Blocks.CARROTS).getStateForAge(0));
        crop(helper, 2, 4, Blocks.FARMLAND, ((CropBlock) Blocks.BEETROOTS).getStateForAge(3));
        crop(helper, 3, 4, Blocks.FARMLAND, ((CropBlock) Blocks.POTATOES).getStateForAge(7));
        crop(helper, 4, 4, Blocks.FARMLAND, Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, 5));
        crop(helper, 5, 2, Blocks.FARMLAND, Blocks.PITCHER_CROP.defaultBlockState().setValue(PitcherCropBlock.AGE, 1));
        crop(helper, 6, 2, Blocks.SOUL_SAND, Blocks.NETHER_WART.defaultBlockState().setValue(NetherWartBlock.AGE, 3));
        crop(helper, 6, 3, Blocks.GRASS_BLOCK, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 1));
        helper.setBlock(new BlockPos(7, 2, 4), Blocks.STONE);
        helper.setBlock(new BlockPos(5, 2, 4), Blocks.SHORT_GRASS);

        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        ZoneValidation.Result validation = monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 1, 2)), helper.absolutePos(new BlockPos(7, 3, 4))));
        helper.assertTrue(validation == ZoneValidation.Result.OK, "Zone rejected: " + validation);
        // 3 wheat 7/7, 2 wheat 3/7, carrot 0/7, beetroot 3/3, potato 7/7, stem 5/7, pitcher 1/4, wart 3/3, berry 1/3
        float expected = (1 + 1 + 1 + 3 / 7F + 3 / 7F + 0 + 1 + 1 + 5 / 7F + 1 / 4F + 1 + 1 / 3F) / 12F;
        helper.succeedWhen(() -> {
            CropScanResult result = monitor.result().orElse(null);
            helper.assertTrue(result != null, "No scan result yet");
            helper.assertTrue(result.crops() == 12, "Expected 12 crops, got " + result.crops());
            helper.assertTrue(result.ready() == 6, "Expected 6 ready, got " + result.ready());
            helper.assertTrue(result.growing() == 6, "Expected 6 growing, got " + result.growing());
            helper.assertTrue(result.complete(), "Loaded zone reported as partial");
            // Random ticks may advance a crop during the test: allow a small tolerance.
            helper.assertTrue(Math.abs(result.maturity() - expected) < 0.05F, "Maturity " + result.maturity() + " != " + expected);
        });
    }

    @GameTest(template = "large", timeoutTicks = 600)
    public static void largeZoneIsSpreadOverTicks(GameTestHelper helper) {
        for (int x = 1; x <= 64; x += 8) {
            for (int z = 1; z <= 64; z += 8) crop(helper, x, z, Blocks.FARMLAND, wheat(7));
        }
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(32, 3, 32));
        CropZone zone = new CropZone(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(64, 2, 64)));
        helper.assertTrue(monitor.setZone(zone) == ZoneValidation.Result.OK, "4096-block zone rejected");
        long started = helper.getLevel().getGameTime();
        helper.succeedWhen(() -> {
            CropScanResult result = monitor.result().orElse(null);
            helper.assertTrue(result != null, "No scan result yet");
            long elapsed = result.finishedAt() - started;
            helper.assertTrue(result.crops() == 64, "Expected 64 crops, got " + result.crops());
            // 4096 positions with 512 per tick cannot finish in fewer than 8 ticks.
            helper.assertTrue(elapsed >= 7, "Scan was not spread over ticks: " + elapsed);
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_SCAN zone=4096 ticks={}", elapsed);
        });
    }

    @GameTest(template = "empty")
    public static void unloadedChunksAreSkippedNotLoaded(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos far = new BlockPos(29_000_000, 64, 29_000_000);
        int chunkX = far.getX() >> 4;
        int chunkZ = far.getZ() >> 4;
        helper.assertFalse(level.hasChunk(chunkX, chunkZ), "Test chunk unexpectedly loaded");
        CropScanner scanner = new CropScanner();
        CropZone zone = new CropZone(far, far.offset(15, 0, 15));
        scanner.setZone(zone);
        CropScanResult result = null;
        for (int i = 0; i < 20 && result == null; i++) result = scanner.tick(level, CropInspector.INSTANCE);
        helper.assertTrue(result != null, "Scan of unloaded zone did not complete");
        helper.assertTrue(result.unloaded() == zone.volume(), "Unloaded positions not reported");
        helper.assertFalse(result.complete(), "Unloaded zone must be marked partial");
        helper.assertFalse(level.hasChunk(chunkX, chunkZ), "Scanning force-loaded a chunk");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void zoneLimitsAreEnforced(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.assertTrue(ZoneValidation.validate(origin, CropZone.around(origin, 15, 15, 16)) == ZoneValidation.Result.OK,
                "32x32x32 zone (default maximum) should be accepted");
        helper.assertTrue(ZoneValidation.validate(origin, CropZone.around(origin, 16, 15, 16)) == ZoneValidation.Result.TOO_LARGE,
                "Oversized zone accepted");
        helper.assertTrue(ZoneValidation.validate(origin, new CropZone(origin.offset(60, 0, 0), origin.offset(61, 0, 1))) == ZoneValidation.Result.TOO_FAR,
                "Distant zone accepted");
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(1, 1, 1));
        monitor.clearZone();
        helper.assertTrue(monitor.setZone(CropZone.around(origin, 50, 1, 1)) !=ZoneValidation.Result.OK, "Monitor stored an invalid zone");
        helper.assertTrue(monitor.zone().isEmpty(), "Invalid zone was applied");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void zonePersistsButResultIsNotSaved(GameTestHelper helper) {
        crop(helper, 2, 2, Blocks.FARMLAND, wheat(7));
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        CropZone zone = new CropZone(helper.absolutePos(new BlockPos(1, 2, 1)), helper.absolutePos(new BlockPos(3, 2, 3)));
        monitor.setZone(zone);
        helper.succeedWhen(() -> {
            helper.assertTrue(monitor.result().isPresent(), "No result yet");
            var registries = helper.getLevel().registryAccess();
            var saved = monitor.saveWithFullMetadata(registries);
            helper.assertTrue(saved.contains("Zone"), "Zone not saved");
            helper.assertFalse(saved.contains("Result"), "Recalculable scan result was written to disk");
            helper.assertTrue(monitor.getUpdateTag(registries).contains("Result"), "Result not synchronized to clients");
            BlockEntity reloaded = BlockEntity.loadStatic(monitor.getBlockPos(), monitor.getBlockState(), saved, registries);
            helper.assertTrue(reloaded instanceof CropMonitorBlockEntity m && m.zone().equals(monitor.zone()), "Zone not reloaded");
        });
    }

    @GameTest(template = "field")
    public static void connectorSelectsZoneWithTwoPositions(GameTestHelper helper) {
        CropMonitorBlockEntity monitor = monitor(helper, new BlockPos(0, 2, 0));
        monitor.clearZone();
        BlockPos a = new BlockPos(2, 1, 2);
        BlockPos b = new BlockPos(9, 2, 7);
        helper.setBlock(a, Blocks.FARMLAND);
        helper.setBlock(b, Blocks.STONE);
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "zone_tester"));
        ItemStack connector = new ItemStack(ModItems.FARM_CONNECTOR.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, connector);
        player.setShiftKeyDown(true);
        use(helper, player, connector, a);
        use(helper, player, connector, b);
        use(helper, player, connector, new BlockPos(0, 2, 0));
        CropZone expected = new CropZone(helper.absolutePos(a), helper.absolutePos(b));
        helper.assertTrue(monitor.zone().equals(java.util.Optional.of(expected)), "Zone not applied: " + monitor.zone());
        helper.succeed();
    }

    static void use(GameTestHelper helper, net.minecraft.world.entity.player.Player player, ItemStack stack, BlockPos relative) {
        BlockPos absolute = helper.absolutePos(relative);
        var hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void controllerAggregatesMonitors(GameTestHelper helper) {
        for (int x = 2; x <= 4; x++) crop(helper, x, 2, Blocks.FARMLAND, wheat(7));
        for (int x = 2; x <= 5; x++) crop(helper, x, 8, Blocks.FARMLAND, wheat(0));
        CropMonitorBlockEntity north = monitor(helper, new BlockPos(0, 2, 2));
        CropMonitorBlockEntity south = monitor(helper, new BlockPos(0, 2, 8));
        north.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(5, 2, 2))));
        south.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 8)), helper.absolutePos(new BlockPos(5, 2, 8))));
        helper.setBlock(new BlockPos(10, 2, 5), ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(10, 2, 5));
        FarmLinkService.link(helper.getLevel(), controller, north, 32);
        FarmLinkService.link(helper.getLevel(), controller, south, 32);
        helper.succeedWhen(() -> {
            helper.assertTrue(north.result().isPresent() && south.result().isPresent(), "Monitors not scanned yet");
            controller.refreshSummary(helper.getLevel());
            var summary = controller.summary();
            helper.assertTrue(summary.cropAreas() == 2, "Expected 2 crop areas, got " + summary.cropAreas());
            helper.assertTrue(summary.crops() == 7, "Expected 7 crops, got " + summary.crops());
            helper.assertTrue(summary.ready() == 3, "Expected 3 ready, got " + summary.ready());
        });
    }
}
