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
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.metric.Unit;
import fr.lkdm.homecore.api.metric.UpdatePolicy;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import fr.lkdm.homelink.farm.farm.bot.FarmBotSnapshot;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
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
 * HomeCore device of a FarmBot Station: its robot's state, battery, cargo, counters and target,
 * the station output, the START / PAUSE / RETURN HOME buttons (executed only through HomeCore's
 * authorized gateway) and transition events.
 */
public final class FarmBotStationDevice implements DashboardDevice, FarmNetworkMember, Renamable, Switchable {
    public static final ResourceLocation TYPE = FarmIds.FARMBOT_STATION;

    private final FarmBotStationView source;
    private final UUID identity;
    private final Consumer<DeviceEvent> events;
    private final TransitionTracker tracker;
    private final DeviceMetric<Boolean> installed = DeviceMetric.builder(FarmIds.FARMBOT_INSTALLED,
            FarmControllerDevice.metricName(FarmIds.FARMBOT_INSTALLED, "FarmBot installed"), MetricTypes.BOOLEAN, false)
            .updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<FarmBotState> status = DeviceMetric.builder(FarmIds.FARMBOT_STATUS,
            FarmControllerDevice.metricName(FarmIds.FARMBOT_STATUS, "FarmBot status"),
            MetricTypes.enumeration(FarmIds.FARMBOT_STATUS, FarmBotState.class), FarmBotState.DOCKED).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Percentage> battery = FarmControllerDevice.percentage(FarmIds.FARMBOT_BATTERY, "FarmBot battery");
    private final DeviceMetric<Integer> storage = slots(FarmIds.FARMBOT_STORAGE, "FarmBot storage");
    private final DeviceMetric<Integer> harvested = FarmControllerDevice.count(FarmIds.FARMBOT_HARVESTED, "Harvested crops", Unit.NONE);
    private final DeviceMetric<String> target = DeviceMetric.builder(FarmIds.FARMBOT_CURRENT_TARGET,
            FarmControllerDevice.metricName(FarmIds.FARMBOT_CURRENT_TARGET, "Current target"), MetricTypes.STRING, "")
            .updatePolicy(UpdatePolicy.ON_CHANGE).build();
    private final DeviceMetric<Integer> output = slots(FarmIds.STATION_OUTPUT_USAGE, "Station output");
    private final List<DeviceMetric<?>> metrics = List.of(installed, status, battery, storage, harvested, target, output);
    private final List<DeviceAction<?>> actions;
    private final DeviceSchema schema;

    public FarmBotStationDevice(FarmBotStationView source, Consumer<DeviceEvent> events) {
        this.source = Objects.requireNonNull(source, "source");
        this.identity = Objects.requireNonNull(source.deviceId(), "deviceId");
        this.events = Objects.requireNonNull(events, "events");
        this.tracker = new TransitionTracker(identity);
        this.actions = List.of(
                button(FarmIds.ACTION_START, "Start", "Allow the FarmBot to look for and harvest mature crops.", () -> source.setWorking(true)),
                button(FarmIds.ACTION_PAUSE, "Pause", "Stop new tasks; a harvest in progress finishes first.", () -> source.setWorking(false)),
                button(FarmIds.ACTION_RETURN_HOME, "Return home", "Drop the current target and drive back to the station.", source::requestReturn));
        this.schema = DeviceSchema.from(this);
        refresh();
    }

    private static DeviceAction<fr.lkdm.homecore.api.action.Unit> button(ResourceLocation id, String name, String description, Runnable command) {
        return DeviceAction.button(id, Component.translatableWithFallback("action.homelink_farm." + id.getPath(), name))
                .description(Component.translatableWithFallback("action.homelink_farm." + id.getPath() + ".description", description))
                .requiredPermission(Permission.CONTROL.id())
                .handler((context, unit) -> {
                    command.run();
                    return ActionResult.success();
                }).build();
    }

    private static DeviceMetric<Integer> slots(ResourceLocation id, String label) {
        return DeviceMetric.builder(id, FarmControllerDevice.metricName(id, label), MetricTypes.INTEGER, 0)
                .unit(Unit.NONE).range(0, FarmBotEntity.INVENTORY_SIZE, 1).updatePolicy(UpdatePolicy.ON_CHANGE).build();
    }

    /** Copies the station and robot state into the metrics and publishes transition events. Server thread. */
    public void refresh() {
        Optional<FarmBotSnapshot> robot = source.snapshot();
        installed.setValue(robot.isPresent());
        robot.ifPresent(bot -> {
            status.setValue(bot.state());
            battery.setValue(new Percentage(bot.battery()));
            storage.setValue(bot.storage());
            harvested.setValue(bot.harvested());
        });
        // "minecraft:wheat 12 64 -3": stable and language independent.
        target.setValue(robot.flatMap(bot -> bot.targetPos().map(pos -> bot.targetBlock() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ()))
                .orElse(""));
        output.setValue(source.outputUsed());
        if (!isValid()) return;
        for (DeviceEvent event : tracker.farmBot(robot, Instant.now())) events.accept(event);
    }

    @Override public UUID id() { return identity; }
    @Override public ResourceLocation deviceType() { return TYPE; }
    @Override public Component displayName() { return source.displayName().copy(); }
    @Override public ActionResult rename(String name) { source.setCustomName(name); return ActionResult.success(); }
    /** Same state as the Start and Pause buttons. */
    @Override public boolean powered() { return source.working(); }
    @Override public ActionResult setPowered(boolean powered) { source.setWorking(powered); return ActionResult.success(); }
    @Override public Object source() { return source; }
    @Override public List<DeviceMetric<?>> metrics() { return metrics; }
    @Override public List<DeviceAction<?>> actions() { return actions; }
    @Override public Set<ResourceLocation> eventTypes() { return FarmIds.FARMBOT_EVENTS; }
    @Override public DeviceSchema schema() { return schema; }
    @Override public Optional<BlockPos> position() { return source.devicePosition(); }
    @Override public Optional<ResourceKey<Level>> dimension() { return source.deviceDimension(); }

    /** ONLINE while loaded (so it can be commanded remotely); the robot state is in the message. */
    @Override
    public DeviceStatus status() {
        if (!isValid()) return DeviceStatus.OFFLINE;
        return DeviceStatus.ONLINE.withMessage(source.snapshot().map(bot -> bot.state().labelWithFallback())
                .orElseGet(() -> Component.translatableWithFallback("gui.homelink_farm.farmbot.none", "NO FARMBOT")));
    }

    @Override
    public boolean isValid() {
        return source.isOperational() && identity.equals(source.deviceId());
    }
}
