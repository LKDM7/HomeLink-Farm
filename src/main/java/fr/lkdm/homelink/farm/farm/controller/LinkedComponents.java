package fr.lkdm.homelink.farm.farm.controller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;

/** Ordered set of components linked to one controller, keyed by component UUID. */
public final class LinkedComponents {
    public enum AddOutcome { ADDED, UPDATED, FULL }

    private final Map<UUID, LinkedComponent> entries = new LinkedHashMap<>();

    /**
     * Adds or refreshes a component. An already known UUID never counts against capacity.
     * @param capacity maximum number of distinct components
     */
    public AddOutcome add(LinkedComponent component, int capacity) {
        if (entries.containsKey(component.id())) {
            entries.put(component.id(), component);
            return AddOutcome.UPDATED;
        }
        if (entries.size() >= capacity) return AddOutcome.FULL;
        // A position holds one block: drop a stale entry for a component that was replaced there.
        entries.values().removeIf(existing -> existing.pos().equals(component.pos()));
        entries.put(component.id(), component);
        return AddOutcome.ADDED;
    }

    public boolean remove(UUID id) {
        return entries.remove(id) != null;
    }

    public Optional<LinkedComponent> get(UUID id) {
        return Optional.ofNullable(entries.get(id));
    }

    public boolean contains(UUID id) {
        return entries.containsKey(id);
    }

    public int size() {
        return entries.size();
    }

    public int count(FarmComponentKind kind) {
        int count = 0;
        for (LinkedComponent component : entries.values()) {
            if (component.kind() == kind) count++;
        }
        return count;
    }

    public Collection<LinkedComponent> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public void clear() {
        entries.clear();
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (LinkedComponent component : entries.values()) list.add(component.save());
        return list;
    }

    public void load(ListTag list) {
        entries.clear();
        for (int i = 0; i < list.size(); i++) {
            LinkedComponent.load(list.getCompound(i)).ifPresent(component -> entries.put(component.id(), component));
        }
    }

    public List<BlockPos> positions() {
        List<BlockPos> positions = new ArrayList<>(entries.size());
        for (LinkedComponent component : entries.values()) positions.add(component.pos());
        return positions;
    }
}
