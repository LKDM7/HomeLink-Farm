package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Bridge to the public HomeCore API ({@code fr.lkdm.homecore.api}). HomeLink Farm never
 * touches HomeCore internals: it registers providers once, then registers/unregisters live
 * devices from its block entity lifecycle (HomeCore does not scan the world).
 */
public final class HomeCoreIntegration {
    private HomeCoreIntegration() {
    }

    /** Registers the block entity adapters; call once during common setup. */
    public static void registerProviders() {
        DashboardAPI.registerDeviceProvider(ModBlockEntities.FARM_CONTROLLER.get(),
                controller -> new FarmControllerDevice(controller, publisher(controller)));
        DashboardAPI.registerDeviceProvider(ModBlockEntities.IRRIGATION_PUMP.get(),
                pump -> new IrrigationPumpDevice(pump, publisher(pump)));
        DashboardAPI.registerDeviceProvider(ModBlockEntities.FARMBOT_STATION.get(),
                station -> new FarmBotStationDevice(station, publisher(station)));
        HomeLinkFarm.LOGGER.info("HomeLink Farm registered its HomeCore device providers (HomeCore API {})", DashboardAPI.API_VERSION);
    }

    /** Publishes on the HomeCore event bus of the server owning the block entity. */
    private static Consumer<DeviceEvent> publisher(BlockEntity source) {
        return event -> {
            if (source.getLevel() instanceof ServerLevel level) DashboardAPI.events(level.getServer()).publish(event);
        };
    }

    /** Registers a loaded device block entity as a live HomeCore device, resolving UUID collisions. */
    public static <T extends DashboardDevice> Optional<T> register(ServerLevel level, AbstractFarmDeviceBlockEntity device, Class<T> type) {
        DeviceRegistry registry = DashboardAPI.devices(level.getServer());
        if (registry.get(device.deviceId()).isPresent()) {
            // A live device already owns this UUID (e.g. a block copied with its data): give this copy its own identity.
            HomeLinkFarm.LOGGER.warn("{} at {} duplicated device {}; assigning a new identity",
                    device.getType(), device.getBlockPos(), device.deviceId());
            device.resetIdentityAfterCollision();
        }
        Optional<DashboardDevice> discovered = DashboardAPI.providers().discover(device);
        if (discovered.isEmpty() || !type.isInstance(discovered.get())) return Optional.empty();
        try {
            registry.register(discovered.get());
            return Optional.of(type.cast(discovered.get()));
        } catch (IllegalArgumentException exception) {
            HomeLinkFarm.LOGGER.error("HomeCore rejected device {}", device.deviceId(), exception);
            return Optional.empty();
        }
    }

    /** Unregisters a device only if the registry still holds this exact instance. */
    public static void unregister(ServerLevel level, DashboardDevice device) {
        DeviceRegistry registry = DashboardAPI.devices(level.getServer());
        registry.get(device.id()).filter(current -> current == device).ifPresent(current -> registry.unregister(current.id()));
    }
}
