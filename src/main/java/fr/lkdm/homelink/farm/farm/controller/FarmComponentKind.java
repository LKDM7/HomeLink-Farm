package fr.lkdm.homelink.farm.farm.controller;

import java.util.Locale;

/** Kinds of devices that can be grouped under a Farm Controller. */
public enum FarmComponentKind {
    CROP_MONITOR,
    IRRIGATION_PUMP,
    FARMBOT_STATION;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static FarmComponentKind bySerializedName(String name) {
        for (FarmComponentKind kind : values()) {
            if (kind.serializedName().equals(name)) return kind;
        }
        return CROP_MONITOR;
    }
}
