package fr.lkdm.homelink.farm.farm.irrigation;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** How an Irrigation Pump reacts to a redstone signal within one block of it, diagonals included. */
public enum RedstoneMode {
    /** Redstone has no effect (default). */
    IGNORED,
    /** The pump only runs while powered. */
    RUN_WHEN_POWERED,
    /** A redstone signal stops the pump. */
    STOP_WHEN_POWERED;

    public boolean allows(boolean powered) {
        return switch (this) {
            case IGNORED -> true;
            case RUN_WHEN_POWERED -> powered;
            case STOP_WHEN_POWERED -> !powered;
        };
    }

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public Component label() {
        return Component.translatable("redstone_mode.homelink_farm." + name().toLowerCase(Locale.ROOT));
    }

    public static RedstoneMode byName(String name) {
        for (RedstoneMode mode : values()) {
            if (mode.name().equals(name)) return mode;
        }
        return IGNORED;
    }
}
