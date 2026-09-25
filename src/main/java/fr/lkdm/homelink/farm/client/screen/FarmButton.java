package fr.lkdm.homelink.farm.client.screen;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/**
 * Flat bevelled button of the HomeLink Dashboard; keeps vanilla behavior and narration.
 * Labels too long for the button are cut with an ellipsis and shown in full as a tooltip.
 */
public final class FarmButton extends Button {
    private BooleanSupplier accentSelected;

    /** Opt-in gold hover and selected state, used by the help and irrigation overlay toggles. */
    public void accentWhen(BooleanSupplier selected) {
        accentSelected = selected;
    }

    private FarmButton(Builder builder) {
        super(builder);
        fitTooltip();
    }

    public static Builder builder(Component message, OnPress onPress) {
        return new Builder(message, onPress) {
            @Override
            public Button build() {
                return new FarmButton(this);
            }
        };
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        fitTooltip();
    }

    private void fitTooltip() {
        setTooltip(Minecraft.getInstance().font.width(getMessage()) > width - 8 ? Tooltip.create(getMessage()) : null);
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        boolean highlighted = active && isHoveredOrFocused();
        boolean gold = active && accentSelected != null && (isHoveredOrFocused() || accentSelected.getAsBoolean());
        int left = getX(), top = getY();
        int background = gold ? FarmTheme.ACCENT : highlighted ? 0xFF5A5D60 : active ? 0xFF474A4D : 0xFF36383A;
        graphics.fill(left, top, left + width, top + height, 0xFF181A1B);
        graphics.fill(left + 1, top + 1, left + width - 1, top + height - 1, background);
        graphics.fill(left + 1, top + 1, left + width - 1, top + 2, gold ? 0xFFF1D5A9 : active ? 0xFF74787A : 0xFF484B4D);
        graphics.fill(left + 1, top + 2, left + 2, top + height - 1, gold ? 0xFFB18D5C : 0xFF626669);
        if (isFocused() && active) graphics.renderOutline(left, top, width, height, FarmTheme.ACCENT);
        var font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        int available = Math.max(0, width - 8);
        if (font.width(text) > available) text = font.plainSubstrByWidth(text, Math.max(0, available - font.width("…"))) + "…";
        graphics.drawString(font, text, left + (width - font.width(text)) / 2, top + (height - 8) / 2,
                gold ? FarmTheme.SURFACE : active ? FarmTheme.TEXT : 0xFF91948F, false);
    }
}
