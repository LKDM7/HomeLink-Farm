package fr.lkdm.homelink.farm.farm.diagnostic;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Problems HomeLink Farm reports only when Minecraft gives a reliable signal. Mature crops
 * are never reported: they no longer need to grow.
 */
public enum ProblemType {
    /** Farmland under a growing crop has moisture 0: vanilla growth is ~3x slower. */
    DRY_FARMLAND(0xFFD8A040),
    /** Raw light below the crop's own random-tick threshold: it cannot grow at all. */
    LOW_LIGHT(0xFF8080FF),
    /**
     * Irrigable crop outside every sprinkler while other crops of the same zone are covered:
     * the player irrigates this field but missed this spot (insufficient coverage).
     */
    NOT_IRRIGATED(0xFFFF9030),
    /** Covered only by sprinklers whose network does not work (dry, disabled, overloaded, disconnected). */
    IRRIGATION_OFFLINE(0xFFE04040);

    private final int color;

    ProblemType(int color) {
        this.color = color;
    }

    /** ARGB color used by screens and location markers. */
    public int color() {
        return color;
    }

    public Component label() {
        return Component.translatable("problem.homelink_farm." + name().toLowerCase(Locale.ROOT));
    }

    public static ProblemType byId(int id) {
        ProblemType[] values = values();
        return values[Math.floorMod(id, values.length)];
    }
}
