package fr.lkdm.homelink.farm.farm.crop;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * What a Crop Monitor outputs to a comparator. Fractions map linearly to 0..15
 * (0% -> 0, 100% -> 15, rounded); PROBLEMS outputs the problem count capped at 15.
 * No result yet (or no zone) outputs 0.
 */
public enum ComparatorMode {
    MATURITY, READY, IRRIGATION, PROBLEMS;

    public int signal(CropScanResult result) {
        if (result == null) return 0;
        return switch (this) {
            case MATURITY -> scale(result.maturity());
            case READY -> scale(result.readyFraction());
            case IRRIGATION -> scale(result.coverage());
            case PROBLEMS -> Math.min(15, result.problems().total());
        };
    }

    public static int scale(float fraction) {
        return Math.round(Math.max(0, Math.min(1, fraction)) * 15);
    }

    public ComparatorMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component label() {
        return Component.translatable("comparator_mode.homelink_farm." + name().toLowerCase(Locale.ROOT));
    }

    public static ComparatorMode byName(String name) {
        for (ComparatorMode mode : values()) {
            if (mode.name().equals(name)) return mode;
        }
        return MATURITY;
    }
}
