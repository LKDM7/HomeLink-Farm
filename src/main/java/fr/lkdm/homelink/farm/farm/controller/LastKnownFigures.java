package fr.lkdm.homelink.farm.farm.controller;

import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.irrigation.PumpSnapshot;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * In-memory last known figures of a controller's components, used while a component's chunk
 * is unloaded. Small (one entry per linked component) and never saved: after a restart the
 * controller simply waits for its components to load.
 */
public final class LastKnownFigures {
    private final Map<UUID, CropScanResult> monitors = new HashMap<>();
    private final Map<UUID, PumpSnapshot> pumps = new HashMap<>();

    public void remember(UUID component, CropScanResult result) {
        monitors.put(component, result);
    }

    public void remember(UUID component, PumpSnapshot snapshot) {
        pumps.put(component, snapshot);
    }

    public Optional<CropScanResult> monitor(UUID component) {
        return Optional.ofNullable(monitors.get(component));
    }

    public Optional<PumpSnapshot> pump(UUID component) {
        return Optional.ofNullable(pumps.get(component));
    }

    /** Forgets components that are no longer linked. */
    public void retainOnly(LinkedComponents linked) {
        monitors.keySet().removeIf(id -> !linked.contains(id));
        pumps.keySet().removeIf(id -> !linked.contains(id));
    }
}
