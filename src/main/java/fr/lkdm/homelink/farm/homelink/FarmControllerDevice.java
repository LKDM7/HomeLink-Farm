package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.action.DeviceAction;
import fr.lkdm.homecore.api.device.DashboardDevice;
import fr.lkdm.homecore.api.device.DeviceSchema;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.DeviceMetric;
import fr.lkdm.homecore.api.metric.MetricTypes;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import fr.lkdm.homelink.farm.farm.irrigation.GrowthBonus;
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
 * HomeCore device of a Farm Controller. Metric and action definitions are created once and
 * kept stable; {@link #refresh} updates values (HomeCore only sends changed revisions) and
 * publishes transition events.
 */
public final class FarmControllerDevice implements DashboardDevice {
    public static final ResourceLocation TYPE = FarmIds.FARM_CONTROLLER;
    /** crop_ready fires when this share of crops is mature. */
    public static final float CROP_READY_THRESHOLD = 0.90F;

    private final FarmControllerView source;
    private final UUID identity;
    private final Consumer<DeviceEvent> events;
    private final TransitionTracker tracker;
    private final DeviceMetric<Integer> cropCount = count(FarmIds.CROP_COUNT, "Crops", Unit.NONE);
    private final DeviceMetric<Percentage> readyPercentage = percentage(FarmIds.READY_PERCENTAGE, "Ready");
    private final DeviceMetric<Percentage> maturity = percentage(FarmIds.MATURITY, "Maturity");
    private final DeviceMetric<Percentage> coverage = percentage(FarmIds.IRRIGATION_COVERAGE, "Irrigation coverage");
    private final DeviceMetric<Integer> irrigatedCrops = count(FarmIds.IRRIGATED_CROPS, "Irrigated crops", Unit.NONE);
    private final DeviceMetric<Integer> problemCount = count(FarmIds.PROBLEM_COUNT, "Problems", Unit.NONE);
    private final DeviceMetric<Integer> cropAreas = count(FarmIds.CROP_AREAS, "Crop areas", Unit.NONE);
    private final DeviceMetric<Integer> pumps = count(FarmIds.PUMPS, "Irrigation pumps", Unit.NONE);
    private final DeviceMetric<Integer> sprinklers = count(FarmIds.SPRINKLERS_CONNECTED, "Sprinklers", Unit.NONE);
    private final DeviceMetric<Integer> capacity = count(FarmIds.SPRINKLER_CAPACITY, "Sprinkler capacity", Unit.NONE);
    private final DeviceMetric<Percentage> growthBonus = percentage(FarmIds.GROWTH_BONUS, "Growth bonus");
    private final List<DeviceMetric<?>> metrics = List.of(cropCount, readyPercentage, maturity, coverage, irrigatedCrops,
            problemCount, cropAreas, pumps, sprinklers, capacity, growthBonus);
    private final List<DeviceAction<?>> actions;
    private final DeviceSchema schema;

    public FarmControllerDevice(FarmControllerView source, Consumer<DeviceEvent> events) {
        this.source = Objects.requireNonNull(source, "source");
        this.identity = Objects.requireNonNull(source.deviceId(), "deviceId");
        this.events = Objects.requireNonNull(events, "events");
        this.tracker = new TransitionTracker(identity);
        this.actions = List.of(DeviceAction.button(FarmIds.ACTION_RESCAN, Component.translatableWithFallback("action.homelink_farm.rescan", "Rescan crops"))
                .description(Component.translatableWithFallback("action.homelink_farm.rescan.description", "Start a new scan of every linked Crop Monitor."))
                .requiredPermission(Permission.CONTROL.id())
                .handler((context, unit) -> {
                    source.requestRescan();
                    return ActionResult.success();
                }).build());
        this.schema = DeviceSchema.from(this);
    }

    /** Copies the latest summary into the metrics and publishes transition events. Server thread. */
    public void refresh(FarmSummary summary) {
        cropCount.setValue(summary.crops());
        readyPercentage.setValue(percentOf(summary.readyFraction()));
        maturity.setValue(percentOf(summary.maturity()));
        coverage.setValue(percentOf(summary.coverage()));
        irrigatedCrops.setValue(summary.irrigated());
        problemCount.setValue(summary.problemTotal());
        cropAreas.setValue(summary.cropAreas());
        pumps.setValue(summary.pumps());
        sprinklers.setValue(summary.sprinklers());
        capacity.setValue(summary.capacity());
        growthBonus.setValue(summary.irrigated() > 0 ? percentOf((float) GrowthBonus.configuredBonus()) : new Percentage(0));
        if (!isValid()) return;
        for (DeviceEvent event : tracker.controller(summary, CROP_READY_THRESHOLD, Instant.now())) events.accept(event);
    }

    @Override public UUID id() { return identity; }
    @Override public ResourceLocation deviceType() { return TYPE; }
    @Override public Component displayName() { return source.displayName().copy(); }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public List<DeviceAction<?>> actions() { return actions; }
    @Override public Set<ResourceLocation> eventTypes() { return FarmIds.CONTROLLER_EVENTS; }
    @Override public DeviceSchema schema() { return schema; }
    @Override public Optional<BlockPos> position() { return source.devicePosition(); }
    @Override public Optional<ResourceKey<Level>> dimension() { return source.deviceDimension(); }

    /** Always ONLINE while loaded (so authorized actions work); problems are described in the message. */
    @Override
    public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        int problems = source.summary().problemTotal();
        return problems == 0 ? DeviceStatus.ONLINE
                : DeviceStatus.ONLINE.withMessage(Component.translatableWithFallback("status.homelink_farm.problems", problems + " problem(s)", problems));
    }

    @Override
    public boolean isValid() {
        return source.isOperational() && identity.equals(source.deviceId());
    }

    static Percentage percentOf(float fraction) {
        double value = Math.round(Math.max(0, Math.min(1, fraction)) * 1000) / 10.0;
        return new Percentage(value);
    }

    /**
     * Metric label translated where the language is known (client, integrated server) and English on
     * a dedicated server ({@code metric.homelink_farm.<id>} keys).
     */
    static Component metricName(ResourceLocation id, String english) {
        return Component.translatableWithFallback("metric." + id.getNamespace() + "." + id.getPath(), english);
    }

    static DeviceMetric<Integer> count(ResourceLocation id, String label, Unit unit) {
        return DeviceMetric.builder(id, metricName(id, label), MetricTypes.INTEGER, 0)
                .unit(unit).range(0, Integer.MAX_VALUE, 1).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    static DeviceMetric<Percentage> percentage(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, metricName(id, label), MetricTypes.PERCENTAGE, new Percentage(0))
                .unit(Unit.PERCENT).range(0, 100).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }
}
