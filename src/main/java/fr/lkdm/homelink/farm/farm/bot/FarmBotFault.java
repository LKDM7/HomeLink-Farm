package fr.lkdm.homelink.farm.farm.bot;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Why a FarmBot is waiting, stuck or in error; NONE while everything is fine. */
public enum FarmBotFault {
    NONE,
    /** The station it belonged to is gone: the robot must be picked up and installed again. */
    NO_STATION,
    /** Its station is in an unloaded chunk: the robot waits where it is (it never loads chunks). */
    STATION_UNLOADED,
    /** The station is not linked to a Farm Controller with a Crop Monitor. */
    NO_MONITOR,
    /** The chosen Crop Monitor is unloaded or has not finished a scan yet. */
    MONITOR_UNAVAILABLE,
    /** The docking spot in front of the station is obstructed. */
    DOCK_BLOCKED,
    /** No path back to the station was found. */
    HOME_UNREACHABLE,
    /** The last target could not be reached and is ignored for a while. */
    TARGET_UNREACHABLE;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component label() {
        return Component.translatable("farmbot_fault.homelink_farm." + serializedName());
    }

    public static FarmBotFault byId(int id) {
        FarmBotFault[] values = values();
        return id >= 0 && id < values.length ? values[id] : NONE;
    }

    public static FarmBotFault byName(String name) {
        for (FarmBotFault fault : values()) {
            if (fault.serializedName().equals(name)) return fault;
        }
        return NONE;
    }
}
