package fr.lkdm.homelink.farm.farm.irrigation;

/**
 * Central gameplay rule, kept pure for testing: one pump feeds at most
 * {@code maxSprinklersPerPump} (5 by default) sprinklers. A network with N supplying pumps
 * accepts 5 x N sprinklers; above that the WHOLE network is OVER_CAPACITY and no sprinkler
 * irrigates, so the player never has to guess which sprinklers work.
 */
public final class IrrigationRules {
    private IrrigationRules() {
    }

    public static NetworkState evaluate(int supplyingPumps, int sprinklers, int maxSprinklersPerPump, boolean tooLarge) {
        if (tooLarge) return NetworkState.TOO_LARGE;
        if (supplyingPumps <= 0) return NetworkState.INACTIVE;
        if (sprinklers > capacity(supplyingPumps, maxSprinklersPerPump)) return NetworkState.OVER_CAPACITY;
        return NetworkState.ACTIVE;
    }

    public static int capacity(int supplyingPumps, int maxSprinklersPerPump) {
        return Math.max(0, supplyingPumps) * maxSprinklersPerPump;
    }
}
