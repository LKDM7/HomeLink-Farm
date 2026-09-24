package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.ComparatorMode;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.farm.irrigation.RedstoneMode;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Real restart check, run as two separate GameTestServer processes on the same world
 * ({@code -PpersistencePass=write} then {@code read}). Uses fixed world positions.
 */
@GameTestHolder(PersistenceGameTests.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class PersistenceGameTests {
    public static final String NAMESPACE = "homelink_farm_persistence";
    private static final Path EXPECTED = Path.of("homelink_farm_persistence.properties");
    private static final BlockPos CONTROLLER = new BlockPos(200, -58, 200);
    private static final BlockPos MONITOR = CONTROLLER.east(2);
    private static final BlockPos PUMP = CONTROLLER.south(4);
    private static final BlockPos PIPE = PUMP.east();
    private static final BlockPos SPRINKLER = PIPE.above();
    private static final CropZone ZONE = new CropZone(MONITOR.offset(-3, -1, -3), MONITOR.offset(3, 1, 3));

    private PersistenceGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void farmSurvivesRestart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        level.setChunkForced(CONTROLLER.getX() >> 4, CONTROLLER.getZ() >> 4, true);
        String pass = System.getProperty("homelink_farm.persistencePass", "");
        switch (pass) {
            case "write" -> write(helper, level);
            case "read" -> read(helper, level);
            default -> helper.fail("Set homelink_farm.persistencePass to write or read");
        }
    }

    private static void write(GameTestHelper helper, ServerLevel level) {
        level.setBlockAndUpdate(CONTROLLER, ModBlocks.FARM_CONTROLLER.get().defaultBlockState());
        level.setBlockAndUpdate(MONITOR, ModBlocks.CROP_MONITOR.get().defaultBlockState());
        level.setBlockAndUpdate(PUMP.below(2), Blocks.STONE.defaultBlockState());
        for (Direction direction : Direction.Plane.HORIZONTAL) level.setBlockAndUpdate(PUMP.below().relative(direction), Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(PUMP.below(), Blocks.WATER.defaultBlockState());
        level.setBlockAndUpdate(PUMP, ModBlocks.IRRIGATION_PUMP.get().defaultBlockState());
        level.setBlockAndUpdate(PIPE, ModBlocks.WEATHERED_COPPER_PIPE.get().defaultBlockState());
        level.setBlockAndUpdate(SPRINKLER, ModBlocks.COPPER_SPRINKLER.get().defaultBlockState());
        var controller = (FarmControllerBlockEntity) level.getBlockEntity(CONTROLLER);
        var monitor = (CropMonitorBlockEntity) level.getBlockEntity(MONITOR);
        var pump = (IrrigationPumpBlockEntity) level.getBlockEntity(PUMP);
        UUID owner = UUID.fromString("5b0cf1c2-7f7e-4f0b-9d2e-0a7a4c1f9e11");
        controller.setOwner(owner, "persist_owner");
        controller.setCustomName("Persisted Farm");
        monitor.setCustomName("North Field");
        helper.assertTrue(monitor.setZone(ZONE).name().equals("OK"), "zone rejected");
        monitor.setComparatorMode(ComparatorMode.READY);
        FarmLinkService.link(level, controller, monitor, 32);
        FarmLinkService.link(level, controller, pump, 32);
        pump.setEnabled(false);
        pump.setRedstoneMode(RedstoneMode.STOP_WHEN_POWERED);
        var networks = DashboardAPI.networks(level.getServer());
        var network = networks.createNetwork("Persisted Home", owner);
        networks.addDevice(network.id(), controller.deviceId());
        controller.setHomeNetwork(network.id(), network.name());
        Properties expected = new Properties();
        expected.setProperty("controller", controller.deviceId().toString());
        expected.setProperty("monitor", monitor.deviceId().toString());
        expected.setProperty("pump", pump.deviceId().toString());
        expected.setProperty("network", network.id().toString());
        try (Writer writer = Files.newBufferedWriter(EXPECTED)) {
            expected.store(writer, "HomeLink Farm persistence check");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        level.getServer().saveEverything(false, true, true);
        HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PERSISTENCE_WRITE_OK controller={} network={}", controller.deviceId(), network.id());
        helper.succeed();
    }

    private static void read(GameTestHelper helper, ServerLevel level) {
        Properties expected = new Properties();
        try (Reader reader = Files.newBufferedReader(EXPECTED)) {
            expected.load(reader);
        } catch (IOException exception) {
            helper.fail("Run the write pass first: " + exception.getMessage());
            return;
        }
        UUID controllerId = UUID.fromString(expected.getProperty("controller"));
        UUID monitorId = UUID.fromString(expected.getProperty("monitor"));
        UUID pumpId = UUID.fromString(expected.getProperty("pump"));
        UUID networkId = UUID.fromString(expected.getProperty("network"));
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(level.getBlockEntity(CONTROLLER) instanceof FarmControllerBlockEntity, "Controller missing after restart");
                    helper.assertTrue(DashboardAPI.devices(level.getServer()).get(controllerId).isPresent(), "Controller not re-registered in HomeCore");
                })
                .thenExecute(() -> {
                    var controller = (FarmControllerBlockEntity) level.getBlockEntity(CONTROLLER);
                    var monitor = (CropMonitorBlockEntity) level.getBlockEntity(MONITOR);
                    var pump = (IrrigationPumpBlockEntity) level.getBlockEntity(PUMP);
                    helper.assertTrue(controller.deviceId().equals(controllerId), "Controller UUID changed");
                    helper.assertTrue(controller.customName().equals("Persisted Farm"), "Controller name lost");
                    helper.assertTrue(controller.owner().isPresent(), "Owner lost");
                    helper.assertTrue(controller.linkedComponents().contains(monitorId) && controller.linkedComponents().contains(pumpId), "Links lost");
                    helper.assertTrue(controller.homeNetwork().equals(Optional.of(networkId)), "HomeNetwork binding lost");
                    helper.assertTrue(DashboardAPI.networks(level.getServer()).getDevices(networkId).contains(controllerId), "HomeCore network lost the controller");
                    helper.assertTrue(monitor.deviceId().equals(monitorId) && monitor.customName().equals("North Field"), "Monitor identity lost");
                    helper.assertTrue(monitor.zone().equals(Optional.of(ZONE)), "Zone lost: " + monitor.zone());
                    helper.assertTrue(monitor.comparatorMode() == ComparatorMode.READY, "Comparator mode lost");
                    helper.assertTrue(monitor.controllerLink().map(link -> link.controllerId().equals(controllerId)).orElse(false), "Monitor link lost");
                    helper.assertTrue(pump.deviceId().equals(pumpId), "Pump UUID changed");
                    helper.assertFalse(pump.enabled(), "Pump enabled flag lost");
                    helper.assertTrue(pump.redstoneMode() == RedstoneMode.STOP_WHEN_POWERED, "Redstone mode lost");
                    pump.setEnabled(true);
                })
                .thenWaitUntil(() -> {
                    var pump = (IrrigationPumpBlockEntity) level.getBlockEntity(PUMP);
                    helper.assertTrue(pump.snapshot().status() == PumpStatus.ACTIVE && pump.snapshot().sprinklers() == 1,
                            "Irrigation network not rebuilt after restart: " + pump.snapshot());
                })
                .thenExecute(() -> {
                    HomeLinkFarm.LOGGER.info("HOMELINK_FARM_PERSISTENCE_READ_OK controller={} network={}", controllerId, networkId);
                    level.setChunkForced(CONTROLLER.getX() >> 4, CONTROLLER.getZ() >> 4, false);
                })
                .thenSucceed();
    }
}
