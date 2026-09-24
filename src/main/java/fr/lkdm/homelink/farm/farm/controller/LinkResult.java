package fr.lkdm.homelink.farm.farm.controller;

import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Outcome of a server-validated link request, with its user-facing message. */
public enum LinkResult {
    LINKED(true),
    ALREADY_LINKED(true),
    CONTROLLER_MISSING(false),
    CONTROLLER_UNLOADED(false),
    NOT_A_COMPONENT(false),
    NO_PERMISSION(false),
    TOO_FAR(false),
    CONTROLLER_FULL(false);

    private final boolean success;

    LinkResult(boolean success) {
        this.success = success;
    }

    public boolean success() {
        return success;
    }

    public String translationKey() {
        return "message.homelink_farm.link." + name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent message(Object... args) {
        return Component.translatable(translationKey(), args);
    }
}
