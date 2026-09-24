package fr.lkdm.homelink.farm.farm.crop;

import net.minecraft.server.MinecraftServer;

/**
 * Server-wide cap on the number of zone positions examined per tick, shared by every Crop
 * Monitor, so many large farms cannot add up to a lag spike. Server thread only.
 */
public final class ScanBudget {
    private static int tick = Integer.MIN_VALUE;
    private static int remaining;

    private ScanBudget() {
    }

    /** Grants up to {@code wanted} positions for the current tick. */
    public static int take(MinecraftServer server, int wanted, int globalBudget) {
        int now = server.getTickCount();
        if (now != tick) {
            tick = now;
            remaining = globalBudget;
        }
        int granted = Math.max(0, Math.min(wanted, remaining));
        remaining -= granted;
        return granted;
    }
}
