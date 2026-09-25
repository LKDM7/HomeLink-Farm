package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import net.minecraft.client.gui.GuiGraphics;

/** Visual language of the HomeLink Dashboard, shared by every HomeLink Farm screen. */
public final class FarmTheme {
    public static final int BACKGROUND = 0xFF303234;
    public static final int HEADER = 0xFF45474A;
    public static final int SURFACE = 0xFF252729;
    public static final int LINE = 0xFF626568;
    public static final int ACCENT = 0xFFD2B181;
    public static final int TEXT = 0xFFE7E5E0;
    public static final int MUTED = 0xFFAFB1AD;
    public static final int ONLINE = 0xFFA1BD92;
    public static final int WARNING = 0xFFD3B16F;
    public static final int OFFLINE = 0xFFD19A8F;

    private FarmTheme() {
    }

    public static int pumpStatus(PumpStatus status) {
        if (status == PumpStatus.ACTIVE) return ONLINE;
        if (status.isFailure()) return OFFLINE;
        return status == PumpStatus.DISABLED ? MUTED : WARNING;
    }

    public static int farmBotStatus(FarmBotState state) {
        if (state.isFault()) return OFFLINE;
        if (state == FarmBotState.LOW_BATTERY || state == FarmBotState.STORAGE_FULL || state == FarmBotState.PAUSED) return WARNING;
        if (state == FarmBotState.DOCKED || state == FarmBotState.IDLE) return MUTED;
        return ONLINE;
    }

    /** Recessed 18 x 18 item slot whose item sits at (x, y). */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF17191A);
        graphics.fill(x, y, x + 17, y + 17, 0xFF535659);
        graphics.fill(x, y, x + 16, y + 16, SURFACE);
    }

    /** Framed window: dark rim, light bevel, header band, screws in the corners. */
    public static void window(GuiGraphics graphics, int x, int y, int width, int height, int headerHeight) {
        graphics.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xFF141617);
        graphics.fill(x - 2, y - 2, x + width + 2, y + height + 2, 0xFF6B6E70);
        graphics.fill(x, y, x + width, y + height, BACKGROUND);
        graphics.fill(x, y, x + width, y + headerHeight, HEADER);
        graphics.renderOutline(x, y, width, height, LINE);
        screw(graphics, x + 4, y + 4);
        screw(graphics, x + width - 8, y + 4);
        screw(graphics, x + 4, y + height - 8);
        screw(graphics, x + width - 8, y + height - 8);
    }

    /** Recessed surface for grouped content. */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, SURFACE);
        graphics.fill(x, y, x + width, y + 1, 0xFF17191A);
        graphics.fill(x, y, x + 1, y + height, 0xFF17191A);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF535659);
    }

    public static void divider(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 1, LINE);
    }

    public static void screw(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 4, y + 4, 0xFF242628);
        graphics.fill(x, y, x + 3, y + 1, 0xFF727578);
        graphics.fill(x + 1, y + 2, x + 3, y + 3, 0xFF858887);
    }

    /** Status pill of the header: dark socket with a colored light. */
    public static void statusLight(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y, x + 8, y + 8, 0xFF1D1F20);
        graphics.fill(x + 2, y + 2, x + 6, y + 6, color);
    }

    /** Thin gauge: line-colored track, filled part in {@code color}. */
    public static void gauge(GuiGraphics graphics, int x, int y, int width, float fraction, int color) {
        graphics.fill(x, y, x + width, y + 3, LINE);
        graphics.fill(x, y, x + Math.round(width * Math.max(0, Math.min(1, fraction))), y + 3, color);
    }
}
