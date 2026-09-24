package fr.lkdm.homelink.farm.farm.irrigation;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** State of one Irrigation Pump, as shown to players and exposed to HomeCore. */
public enum PumpStatus {
    DISABLED(0xFF909090),
    REDSTONE_STOPPED(0xFFB04040),
    NO_WATER(0xFFE05050),
    NO_SPRINKLERS(0xFFE0C050),
    ACTIVE(0xFF58C858),
    OVER_CAPACITY(0xFFE05050),
    NETWORK_TOO_LARGE(0xFFE05050);

    /** Comparator output of a pump in a failure state. */
    public static final int FAULT_SIGNAL = 15;

    private final int color;

    PumpStatus(int color) {
        this.color = color;
    }

    public int color() {
        return color;
    }

    public boolean isFailure() {
        return this == NO_WATER || this == OVER_CAPACITY || this == NETWORK_TOO_LARGE;
    }

    public Component label() {
        return Component.translatable("pump_status.homelink_farm." + name().toLowerCase(Locale.ROOT));
    }

    /** Translated where the language is known (client, integrated server), English on a dedicated server. */
    public Component labelWithFallback() {
        String english = name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT).replace('_', ' ');
        return Component.translatableWithFallback("pump_status.homelink_farm." + name().toLowerCase(Locale.ROOT), english);
    }

    /**
     * Comparator output: number of sprinklers fed (1..14) while ACTIVE, 15 for a failure
     * (no water, over capacity, network too large), 0 otherwise (disabled, stopped, no sprinkler).
     */
    public int comparatorSignal(int sprinklers) {
        if (this == ACTIVE) return Math.max(1, Math.min(14, sprinklers));
        return isFailure() ? FAULT_SIGNAL : 0;
    }

    public static PumpStatus of(boolean enabled, boolean redstoneAllows, boolean water, IrrigationNetwork network) {
        if (!enabled) return DISABLED;
        if (!redstoneAllows) return REDSTONE_STOPPED;
        if (!water) return NO_WATER;
        return switch (network.state()) {
            case TOO_LARGE -> NETWORK_TOO_LARGE;
            case OVER_CAPACITY -> OVER_CAPACITY;
            default -> network.sprinklers().isEmpty() ? NO_SPRINKLERS : ACTIVE;
        };
    }

    public static PumpStatus byId(int id) {
        PumpStatus[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}
