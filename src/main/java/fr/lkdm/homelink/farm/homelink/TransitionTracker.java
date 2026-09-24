package fr.lkdm.homelink.farm.homelink;

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

    private DeviceEvent event(net.minecraft.resources.ResourceLocation type, DeviceEvent.Severity severity, Instant now,
                              Map<String, String> data) {
        return new DeviceEvent(type, source, now, severity, data);
    }

    private static String percent(float fraction) {
        return String.format(Locale.ROOT, "%.1f", fraction * 100);
    }
}
