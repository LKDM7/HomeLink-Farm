package fr.lkdm.homelink.farm.farm.crop;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Positions of loaded Crop Monitors per level (maintained by their block entity lifecycle). */
public final class LoadedMonitors {
    private static final Map<ServerLevel, Set<BlockPos>> MONITORS = new WeakHashMap<>();

    private LoadedMonitors() {
    }

    public static void add(ServerLevel level, BlockPos pos) {
        MONITORS.computeIfAbsent(level, key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    public static void remove(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = MONITORS.get(level);
        if (set != null) set.remove(pos);
    }

    public static Set<BlockPos> in(ServerLevel level) {
        return Collections.unmodifiableSet(MONITORS.getOrDefault(level, Set.of()));
    }

    public static void forget(ServerLevel level) {
        MONITORS.remove(level);
    }
}
