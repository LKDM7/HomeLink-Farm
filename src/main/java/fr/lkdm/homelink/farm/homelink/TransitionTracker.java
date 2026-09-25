package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homelink.farm.farm.bot.FarmBotSnapshot;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Turns successive states into HomeCore events on TRANSITIONS only, so a condition that lasts
 * produces one event, never one per tick. The first observation after loading only records the
 * state (a reload must not replay old events).
 */
public final class TransitionTracker {
    /** crop_ready re-arms once the ready share falls this far below the threshold. */
    public static final float READY_HYSTERESIS = 0.20F;

    private final UUID source;
    private boolean initialized;
    private boolean readyArmed = true;
    private int problems;
    private int pumpFaults;
    private PumpStatus pumpStatus;
    private FarmBotState botState;
    /** Harvest counter when the last harvest_complete was published. */
    private int harvestBaseline;

    public TransitionTracker(UUID source) {
        this.source = source;
    }

    /** Controller transitions: crop_ready, problem_detected, irrigation_failure / irrigation_restored. */
    public List<DeviceEvent> controller(FarmSummary summary, float readyThreshold, Instant now) {
        List<DeviceEvent> events = new ArrayList<>();
        float ready = summary.readyFraction();
        boolean readyReached = summary.crops() > 0 && ready >= readyThreshold;
        int newProblems = summary.problemTotal();
        if (!initialized) {
            initialized = true;
            readyArmed = !readyReached;
            problems = newProblems;
            pumpFaults = summary.pumpFaults();
            return events;
        }
        if (readyReached && readyArmed) {
            readyArmed = false;
            events.add(event(FarmIds.CROP_READY, DeviceEvent.Severity.INFO, now, Map.of(
                    "ready_percentage", percent(ready), "crops", Integer.toString(summary.crops()),
                    "ready", Integer.toString(summary.ready()))));
        } else if (!readyArmed && ready < readyThreshold - READY_HYSTERESIS) {
            readyArmed = true;
        }
        if (newProblems > 0 && problems == 0) {
            Map<String, String> data = new LinkedHashMap<>();
            data.put("problem_count", Integer.toString(newProblems));
            data.put("pump_faults", Integer.toString(summary.pumpFaults()));
            for (ProblemType type : ProblemType.values()) {
                if (summary.problems().get(type) > 0) data.put(type.name().toLowerCase(Locale.ROOT), Integer.toString(summary.problems().get(type)));
            }
            events.add(event(FarmIds.PROBLEM_DETECTED, DeviceEvent.Severity.WARNING, now, data));
        }
        problems = newProblems;
        if (summary.pumpFaults() > 0 && pumpFaults == 0) {
            events.add(event(FarmIds.IRRIGATION_FAILURE, DeviceEvent.Severity.WARNING, now,
                    Map.of("pump_faults", Integer.toString(summary.pumpFaults()))));
        } else if (summary.pumpFaults() == 0 && pumpFaults > 0) {
            events.add(event(FarmIds.IRRIGATION_RESTORED, DeviceEvent.Severity.INFO, now, Map.of("pumps", Integer.toString(summary.pumps()))));
        }
        pumpFaults = summary.pumpFaults();
        return events;
    }

    /** Pump transitions: pump_over_capacity, irrigation_failure / irrigation_restored. */
    public List<DeviceEvent> pump(PumpStatus status, int sprinklers, int capacity, Instant now) {
        List<DeviceEvent> events = new ArrayList<>();
        PumpStatus previous = pumpStatus;
        pumpStatus = status;
        if (!initialized) {
            initialized = true;
            return events;
        }
        if (previous == status) return events;
        Map<String, String> data = Map.of("status", status.name().toLowerCase(Locale.ROOT),
                "sprinklers", Integer.toString(sprinklers), "capacity", Integer.toString(capacity));
        if (status == PumpStatus.OVER_CAPACITY) {
            events.add(event(FarmIds.PUMP_OVER_CAPACITY, DeviceEvent.Severity.WARNING, now, data));
        }
        if (status.isFailure() && (previous == null || !previous.isFailure())) {
            events.add(event(FarmIds.IRRIGATION_FAILURE, DeviceEvent.Severity.WARNING, now, data));
        } else if (status == PumpStatus.ACTIVE && previous != null && previous.isFailure()) {
            events.add(event(FarmIds.IRRIGATION_RESTORED, DeviceEvent.Severity.INFO, now, data));
        }
        return events;
    }

    /**
     * FarmBot transitions: low battery, storage full, stuck, output blocked, returned, and
     * harvest_complete when the robot comes home with nothing left to harvest after a job.
     */
    public List<DeviceEvent> farmBot(java.util.Optional<FarmBotSnapshot> snapshot, Instant now) {
        List<DeviceEvent> events = new ArrayList<>();
        FarmBotState previous = botState;
        FarmBotState current = snapshot.map(FarmBotSnapshot::state).orElse(null);
        botState = current;
        if (!initialized) {
            initialized = true;
            harvestBaseline = snapshot.map(FarmBotSnapshot::harvested).orElse(0);
            return events;
        }
        if (current == null || current == previous) return events;
        FarmBotSnapshot bot = snapshot.get();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("status", bot.state().serializedName());
        data.put("fault", bot.fault().serializedName());
        data.put("battery", Integer.toString(bot.battery()));
        data.put("storage", Integer.toString(bot.storage()));
        data.put("harvested", Integer.toString(bot.harvested()));
        switch (current) {
            case LOW_BATTERY -> events.add(event(FarmIds.FARMBOT_LOW_BATTERY, DeviceEvent.Severity.WARNING, now, data));
            case STORAGE_FULL -> events.add(event(FarmIds.FARMBOT_STORAGE_FULL, DeviceEvent.Severity.INFO, now, data));
            case STUCK -> events.add(event(FarmIds.FARMBOT_STUCK, DeviceEvent.Severity.WARNING, now, data));
            case OUTPUT_BLOCKED -> events.add(event(FarmIds.FARMBOT_OUTPUT_BLOCKED, DeviceEvent.Severity.WARNING, now, data));
            case DOCKED -> {
                if (previous == null || !(previous.returning() || previous == FarmBotState.STUCK)) break;
                events.add(event(FarmIds.FARMBOT_RETURNED, DeviceEvent.Severity.INFO, now, data));
                if (previous == FarmBotState.RETURNING && bot.harvested() > harvestBaseline) {
                    Map<String, String> job = new LinkedHashMap<>(data);
                    job.put("crops", Integer.toString(bot.harvested() - harvestBaseline));
                    events.add(event(FarmIds.FARMBOT_HARVEST_COMPLETE, DeviceEvent.Severity.INFO, now, job));
                    harvestBaseline = bot.harvested();
                }
            }
            default -> { }
        }
        return events;
    }

    private DeviceEvent event(net.minecraft.resources.ResourceLocation type, DeviceEvent.Severity severity, Instant now,
                              Map<String, String> data) {
        return new DeviceEvent(type, source, now, severity, data);
    }

    private static String percent(float fraction) {
        return String.format(Locale.ROOT, "%.1f", fraction * 100);
    }
}
