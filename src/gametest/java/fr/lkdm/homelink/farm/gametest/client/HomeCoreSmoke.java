package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.check;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pressKey;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.client.ClientDeviceCache;
import fr.lkdm.homecore.api.client.HomeCoreClient;
import fr.lkdm.homecore.api.transport.HomeCorePayloads;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.client.ClientFarmData;
import fr.lkdm.homelink.farm.client.screen.FarmControllerScreen;
import fr.lkdm.homelink.farm.client.screen.IrrigationPumpScreen;
import fr.lkdm.homelink.farm.homelink.FarmIds;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Phase 6 client checks: HomeNetwork binding from the screens and the HomeCore client (Dashboard) path. */
final class HomeCoreSmoke {
    private static volatile UUID network;
    private static UUID actionRequest;

    private HomeCoreSmoke() {
    }

    static void define() {
        step("create home network", () -> true, () -> onServer(player ->
                network = DashboardAPI.networks(player.server).createNetwork("Smoke Home", player.getUUID()).id()));
        bind("controller", SmokeScenario.CONTROLLER, FarmControllerScreen.class, true);
        bind("pump", IrrigationSmoke.FIELD_PUMP, IrrigationPumpScreen.class, false);
        step("request devices", () -> network != null, () -> HomeCoreClient.requestDevices(Optional.of(network), 0));
        step("dashboard snapshot", () -> snapshot(SmokeScenario.CONTROLLER) != null && snapshot(IrrigationSmoke.FIELD_PUMP) != null, () -> {
            CompoundTag controller = snapshot(SmokeScenario.CONTROLLER);
            check(controller.getString("type").equals(FarmIds.FARM_CONTROLLER.toString()), "wrong device type " + controller.getString("type"));
            CompoundTag crops = metric(controller, FarmIds.CROP_COUNT.toString());
            check(crops != null, "crop_count metric missing from the HomeCore snapshot");
            check(metric(controller, FarmIds.IRRIGATION_COVERAGE.toString()) != null, "irrigation_coverage missing");
            CompoundTag pump = snapshot(IrrigationSmoke.FIELD_PUMP);
            check(metric(pump, FarmIds.OVER_CAPACITY.toString()) != null, "over_capacity metric missing");
            check(pump.getList("actions", Tag.TAG_COMPOUND).size() == 1, "pump action missing");
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_HOMECORE_SNAPSHOT controller={} name='{}' status={} crop_count={} pump_actions={}",
                    controller.getUUID("id"), controller.getString("name"), controller.getString("status"), crops.get("value"),
                    pump.getList("actions", Tag.TAG_COMPOUND).getCompound(0).getString("id"));
        });
        step("disable pump through HomeCore", () -> true, () -> actionRequest = HomeCoreClient.executeAction(network,
                device(IrrigationSmoke.FIELD_PUMP).deviceId(), FarmIds.ACTION_ENABLED, false));
        step("pump disabled", () -> actionSucceeded() && !((IrrigationPumpBlockEntity) device(IrrigationSmoke.FIELD_PUMP)).enabled(), () -> { });
        step("enable pump through HomeCore", () -> true, () -> actionRequest = HomeCoreClient.executeAction(network,
                device(IrrigationSmoke.FIELD_PUMP).deviceId(), FarmIds.ACTION_ENABLED, true));
        step("pump enabled", () -> actionSucceeded() && ((IrrigationPumpBlockEntity) device(IrrigationSmoke.FIELD_PUMP)).enabled(),
                () -> HomeLinkFarm.LOGGER.info("HOMELINK_FARM_HOMECORE_CLIENT_OK network={}", network));
        RedstoneSmoke.define();
    }

    private static void bind(String name, BlockPos pos, Class<?> screen, boolean screenshot) {
        step("open " + name, () -> true, () -> onServer(player -> {
            // Walk up to the device like a player would (screens close beyond 8 blocks).
            player.teleportTo(player.serverLevel(), pos.getX() + 0.5, pos.getY() + 1, pos.getZ() - 2.5, 0, 30);
            player.openMenu((AbstractFarmDeviceBlockEntity) player.level().getBlockEntity(pos), pos);
        }));
        step(name + " choices", () -> screen.isInstance(Minecraft.getInstance().screen) && !ClientFarmData.networkChoices(pos).isEmpty(), () -> { });
        step("press network " + name, () -> true, () -> pressKey("gui.homelink_farm.network"));
        step(name + " bound", () -> device(pos) != null && device(pos).homeNetwork().equals(Optional.of(network)), () -> { });
        pause(10);
        if (screenshot) step(name + " network screenshot", () -> true, () -> screenshot(name + "_network"));
        step("close", () -> true, () -> Minecraft.getInstance().player.closeContainer());
    }

    private static AbstractFarmDeviceBlockEntity device(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(pos) instanceof AbstractFarmDeviceBlockEntity device ? device : null;
    }

    private static CompoundTag snapshot(BlockPos pos) {
        AbstractFarmDeviceBlockEntity device = device(pos);
        if (device == null || network == null) return null;
        var snapshot = ClientDeviceCache.INSTANCE.devices().get(new ClientDeviceCache.DeviceKey(network, device.deviceId()));
        return snapshot == null ? null : snapshot.data();
    }

    private static CompoundTag metric(CompoundTag device, String id) {
        var metrics = device.getList("metrics", Tag.TAG_COMPOUND);
        for (int i = 0; i < metrics.size(); i++) {
            if (metrics.getCompound(i).getString("id").equals(id)) return metrics.getCompound(i);
        }
        return null;
    }

    private static boolean actionSucceeded() {
        return ClientDeviceCache.INSTANCE.recentMessages().stream()
                .filter(HomeCorePayloads.ActionResultResponse.class::isInstance)
                .map(HomeCorePayloads.ActionResultResponse.class::cast)
                .anyMatch(response -> response.requestId().equals(actionRequest) && response.result().isSuccess());
    }
}
