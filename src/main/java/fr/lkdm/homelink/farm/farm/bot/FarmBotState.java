package fr.lkdm.homelink.farm.farm.bot;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Displayed state of a FarmBot. Decided on the server by {@link FarmBotBrain}; clients only
 * render it (model animations, station screen, HomeCore).
 */
public enum FarmBotState {
    DOCKED,
    IDLE,
    SEARCHING,
    MOVING,
    HARVESTING,
    RETURNING,
    UNLOADING,
    CHARGING,
    PAUSED,
    STORAGE_FULL,
    OUTPUT_BLOCKED,
    LOW_BATTERY,
    OUT_OF_POWER,
    STUCK,
    ERROR;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component label() {
        return Component.translatable("farmbot_status.homelink_farm." + serializedName());
    }

    /** Same label with an English fallback, for texts built on a dedicated server (HomeCore). */
    public Component labelWithFallback() {
        return Component.translatableWithFallback("farmbot_status.homelink_farm." + serializedName(), name().replace('_', ' '));
    }

    /** Driving back to the station (the three reasons are shown as distinct states). */
    public boolean returning() {
        return this == RETURNING || this == LOW_BATTERY || this == STORAGE_FULL;
    }

    /** Needs the player (or the world) to change something. */
    public boolean isFault() {
        return this == OUTPUT_BLOCKED || this == OUT_OF_POWER || this == STUCK || this == ERROR;
    }

    public static FarmBotState byId(int id) {
        FarmBotState[] values = values();
        return id >= 0 && id < values.length ? values[id] : ERROR;
    }

    public static FarmBotState byName(String name) {
        for (FarmBotState state : values()) {
            if (state.serializedName().equals(name)) return state;
        }
        return DOCKED;
    }
}
