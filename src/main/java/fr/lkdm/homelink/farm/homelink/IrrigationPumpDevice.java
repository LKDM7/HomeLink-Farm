package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.device.Renamable;
import fr.lkdm.homecore.api.device.Switchable;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homelink.farm.farm.irrigation.PumpSnapshot;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * HomeCore device of an Irrigation Pump: hydraulic metrics, the standard power and rename
 * actions (executed only through HomeCore's authorized gateway) and transition events.
 */
public final class IrrigationPumpDevice implements DashboardDevice, FarmNetworkMember, Renamable, Switchable {
    public static final ResourceLocation TYPE = FarmIds.IRRIGATION_PUMP;

    private final PumpView source;
    private final UUID identity;
    private final Consumer<DeviceEvent> events;
    private final TransitionTracker tracker;
    private final DeviceMetric<PumpStatus> status = DeviceMetric.builder(FarmIds.PUMP_STATUS, FarmControllerDevice.metricName(FarmIds.PUMP_STATUS, "Pump status"),
            MetricTypes.enumeration(FarmIds.PUMP_STATUS, PumpStatus.class), PumpStatus.DISABLED).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Boolean> enabled = flag(FarmIds.ENABLED, "Enabled");
    private final DeviceMetric<Boolean> water = flag(FarmIds.WATER_AVAILABLE, "Water available");
    private final DeviceMetric<Integer> sprinklers = FarmControllerDevice.count(FarmIds.SPRINKLERS_CONNECTED, "Sprinklers connected",
            fr.lkdm.homecore.api.metric.Unit.NONE);
    private final DeviceMetric<Integer> capacity = FarmControllerDevice.count(FarmIds.SPRINKLER_CAPACITY, "Sprinkler capacity",
            fr.lkdm.homecore.api.metric.Unit.NONE);
    private final DeviceMetric<Integer> irrigatedCrops = FarmControllerDevice.count(FarmIds.IRRIGATED_CROPS, "Irrigated crops",
            fr.lkdm.homecore.api.metric.Unit.NONE);
    private final DeviceMetric<Boolean> overCapacity = flag(FarmIds.OVER_CAPACITY, "Over capacity");
    private final List<DeviceMetric<?>> metrics = List.of(status, enabled, water, sprinklers, capacity, irrigatedCrops, overCapacity);
    private final List<DeviceAction<?>> actions;
    private final DeviceSchema schema;

    public IrrigationPumpDevice(PumpView source, Consumer<DeviceEvent> events) {
        this.source = Objects.requireNonNull(source, "source");
        this.identity = Objects.requireNonNull(source.deviceId(), "deviceId");
        this.events = Objects.requireNonNull(events, "events");
        this.tracker = new TransitionTracker(identity);
        // The on/off switch is HomeCore's standard power action, from Switchable.
        this.actions = List.of();
        this.schema = DeviceSchema.from(this);
        enabled.setValue(source.enabled());
    }

    /** Copies the pump state into the metrics and publishes transition events. Server thread. */
    public void refresh(PumpSnapshot snapshot, boolean pumpEnabled) {
        status.setValue(snapshot.status());
        enabled.setValue(pumpEnabled);
        water.setValue(snapshot.water());
        sprinklers.setValue(snapshot.sprinklers());
        capacity.setValue(snapshot.capacity());
        irrigatedCrops.setValue(snapshot.irrigatedCrops());
        overCapacity.setValue(snapshot.overCapacity());
        if (!isValid()) return;
        for (DeviceEvent event : tracker.pump(snapshot.status(), snapshot.sprinklers(), snapshot.capacity(), Instant.now())) {
            events.accept(event);
        }
    }

    @Override public UUID id() { return identity; }
    @Override public ResourceLocation deviceType() { return TYPE; }
    @Override public Component displayName() { return source.displayName().copy(); }
    @Override public ActionResult rename(String name) { source.setCustomName(name); return ActionResult.success(); }
    @Override public boolean powered() { return source.enabled(); }
    @Override public ActionResult setPowered(boolean powered) { source.setEnabled(powered); return ActionResult.success(); }
    @Override public Object source() { return source; }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public List<DeviceAction<?>> actions() { return actions; }
    @Override public Set<ResourceLocation> eventTypes() { return FarmIds.PUMP_EVENTS; }
    @Override public DeviceSchema schema() { return schema; }
    @Override public Optional<BlockPos> position() { return source.devicePosition(); }
    @Override public Optional<ResourceKey<Level>> dimension() { return source.deviceDimension(); }

    /** ONLINE while loaded so it can be re-enabled remotely; the pump state is in the message. */
    @Override
    public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        return DeviceStatus.ONLINE.withMessage(source.snapshot().status().labelWithFallback());
    }

    @Override
    public boolean isValid() {
        return source.isOperational() && identity.equals(source.deviceId());
    }

    private static DeviceMetric<Boolean> flag(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, FarmControllerDevice.metricName(id, label), MetricTypes.BOOLEAN, false).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }
}
