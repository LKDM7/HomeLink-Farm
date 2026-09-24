package fr.lkdm.homelink.farm.gametest;

import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.PUMP;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.buildLine;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.pump;
import static fr.lkdm.homelink.farm.gametest.IrrigationNetworkGameTests.sprinkler;

import com.mojang.authlib.GameProfile;
import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.network.NetworkRole;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.homelink.FarmIds;
import fr.lkdm.homelink.farm.homelink.HomeNetworkBinding;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Phase 6: Farm Controller and Irrigation Pump exposed through the real HomeCore API. */
@GameTestHolder(HomeLinkFarmGameTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HomeCoreGameTests {
    private HomeCoreGameTests() {
    }

    static ServerPlayer player(GameTestHelper helper, String name) {
        return FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
    }

    static Object metric(fr.lkdm.homecore.api.device.DashboardDevice device, ResourceLocation id) {
        return device.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst().map(DeviceMetric::value).orElseThrow();
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void controllerMetricsReachHomeCore(GameTestHelper helper) {
        IrrigationGrowthGameTests.plant(helper, 2, 7, 5, 6, 7);
        IrrigationGrowthGameTests.plant(helper, 2, 7, 7, 7, 1);
        helper.setBlock(new BlockPos(0, 2, 5), ModBlocks.CROP_MONITOR.get());
        CropMonitorBlockEntity monitor = helper.getBlockEntity(new BlockPos(0, 2, 5));
        monitor.setZone(new CropZone(helper.absolutePos(new BlockPos(2, 2, 5)), helper.absolutePos(new BlockPos(7, 2, 7))));
        helper.setBlock(new BlockPos(10, 2, 5), ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(10, 2, 5));
        FarmLinkService.link(helper.getLevel(), controller, monitor, 32);
        helper.succeedWhen(() -> {
            helper.assertTrue(monitor.result().isPresent(), "Monitor not scanned yet");
            controller.refreshSummary(helper.getLevel());
            var device = DashboardAPI.devices(helper.getLevel().getServer()).get(controller.deviceId()).orElse(null);
            helper.assertTrue(device != null, "Controller not registered in HomeCore");
            helper.assertTrue(metric(device, FarmIds.CROP_COUNT).equals(18), "crop_count = " + metric(device, FarmIds.CROP_COUNT));
            var ready = (fr.lkdm.homecore.api.metric.Percentage) metric(device, FarmIds.READY_PERCENTAGE);
            helper.assertTrue(Math.abs(ready.value() - 66.7) < 0.1, "ready_percentage = " + ready);
            helper.assertTrue(device.schema().actions().stream().anyMatch(action -> action.id().equals(FarmIds.ACTION_RESCAN)), "rescan action missing");
        });
    }

    @GameTest(template = "field", timeoutTicks = 300)
    public static void pumpActionGoesThroughHomeCorePermissions(GameTestHelper helper) {
        buildLine(helper, 1, true);
        IrrigationPumpBlockEntity pump = pump(helper, PUMP);
        ServerPlayer owner = player(helper, "farm_owner");
        ServerPlayer viewer = player(helper, "farm_viewer");
        pump.setOwner(owner.getUUID(), "farm_owner");
        var networks = DashboardAPI.networks(helper.getLevel().getServer());
        var network = networks.createNetwork("HomeLink Farm test", owner.getUUID());
        networks.setMember(network.id(), viewer.getUUID(), NetworkRole.VIEWER);
        helper.succeedWhen(() -> {
            helper.assertTrue(pump.homeCoreDevice().isPresent(), "Pump not registered in HomeCore");
            helper.assertTrue(pump.snapshot().status() == PumpStatus.ACTIVE, "Pump not active yet");
            if (pump.homeNetwork().isEmpty()) {
                var bound = HomeNetworkBinding.bind(owner, pump, Optional.of(network.id()));
                helper.assertTrue(bound == HomeNetworkBinding.Result.BOUND, "Owner could not bind: " + bound);
            }
            helper.assertTrue(networks.getDevices(network.id()).contains(pump.deviceId()), "Pump not in the HomeNetwork");
            ActionResult denied = DashboardAPI.executeAction(viewer, network.id(), pump.deviceId(), FarmIds.ACTION_ENABLED, false);
            helper.assertTrue(denied.code() == ActionResult.Code.DENIED, "VIEWER was allowed to control the pump: " + denied.code());
            helper.assertTrue(pump.enabled(), "Denied action changed the pump");
            ActionResult allowed = DashboardAPI.executeAction(owner, network.id(), pump.deviceId(), FarmIds.ACTION_ENABLED, false);
            helper.assertTrue(allowed.isSuccess(), "Owner action failed: " + allowed.code());
            helper.assertFalse(pump.enabled(), "HomeCore action did not disable the pump");
            ActionResult invalid = DashboardAPI.executeAction(owner, network.id(), pump.deviceId(), FarmIds.ACTION_ENABLED, "yes");
            helper.assertTrue(invalid.code() == ActionResult.Code.INVALID_PARAMETER, "Invalid parameter accepted");
            networks.deleteNetwork(network.id());
        });
    }

    @GameTest(template = "field")
    public static void bindingRequiresManageNetwork(GameTestHelper helper) {
        helper.setBlock(new BlockPos(2, 2, 2), ModBlocks.FARM_CONTROLLER.get());
        FarmControllerBlockEntity controller = helper.getBlockEntity(new BlockPos(2, 2, 2));
        ServerPlayer owner = player(helper, "network_owner");
        ServerPlayer member = player(helper, "network_member");
        controller.setOwner(member.getUUID(), "network_member");
        var networks = DashboardAPI.networks(helper.getLevel().getServer());
        var network = networks.createNetwork("Binding test", owner.getUUID());
        networks.setMember(network.id(), member.getUUID(), NetworkRole.MEMBER);
        var result = HomeNetworkBinding.bind(member, controller, Optional.of(network.id()));
        helper.assertTrue(result == HomeNetworkBinding.Result.DENIED, "MEMBER without MANAGE_NETWORK could bind: " + result);
        helper.assertFalse(networks.getDevices(network.id()).contains(controller.deviceId()), "Device added despite denial");
        helper.assertTrue(HomeNetworkBinding.bind(member, controller, Optional.of(UUID.randomUUID())) == HomeNetworkBinding.Result.UNKNOWN_NETWORK,
                "Unknown network accepted");
        networks.setMember(network.id(), member.getUUID(), NetworkRole.ADMIN);
        helper.assertTrue(HomeNetworkBinding.bind(member, controller, Optional.of(network.id())) == HomeNetworkBinding.Result.BOUND, "ADMIN could not bind");
        helper.destroyBlock(new BlockPos(2, 2, 2));
        helper.assertFalse(networks.getDevices(network.id()).contains(controller.deviceId()), "Broken controller stayed in the network");
        networks.deleteNetwork(network.id());
        helper.succeed();
    }

    @GameTest(template = "field", timeoutTicks = 500)
    public static void pumpEventsAreTransitionsNotSpam(GameTestHelper helper) {
        buildLine(helper, 5, true);
        IrrigationPumpBlockEntity pump = pump(helper, PUMP);
        List<DeviceEvent> events = new CopyOnWriteArrayList<>();
        var subscription = DashboardAPI.events(helper.getLevel().getServer()).subscribe(event -> {
            if (event.source().equals(pump.deviceId())) events.add(event);
        });
        BlockPos sixth = sprinkler(5);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.ACTIVE, "Not active"))
                .thenExecute(() -> {
                    for (int x = 12; x <= 13; x++) helper.setBlock(new BlockPos(x, 2, 1), ModBlocks.COPPER_PIPE.get());
                    helper.setBlock(sixth, ModBlocks.COPPER_SPRINKLER.get());
                })
                .thenWaitUntil(() -> helper.assertTrue(pump.snapshot().status() == PumpStatus.OVER_CAPACITY, "Not over capacity"))
                .thenIdle(60)
                .thenExecute(() -> {
                    helper.assertTrue(count(events, FarmIds.PUMP_OVER_CAPACITY) == 1, "pump_over_capacity sent " + count(events, FarmIds.PUMP_OVER_CAPACITY) + " times");
                    helper.assertTrue(count(events, FarmIds.IRRIGATION_FAILURE) == 1, "irrigation_failure sent " + count(events, FarmIds.IRRIGATION_FAILURE) + " times");
                    helper.destroyBlock(sixth);
                })
                .thenWaitUntil(() -> helper.assertTrue(count(events, FarmIds.IRRIGATION_RESTORED) == 1, "irrigation_restored not sent"))
                .thenExecute(subscription::close)
                .thenSucceed();
    }

    static long count(List<DeviceEvent> events, ResourceLocation type) {
        return events.stream().filter(event -> event.type().equals(type)).count();
    }
}
