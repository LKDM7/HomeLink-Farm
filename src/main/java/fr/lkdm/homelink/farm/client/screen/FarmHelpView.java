package fr.lkdm.homelink.farm.client.screen;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

/** Local machine instructions, wrapped to the available space and scrollable by mouse or keyboard. */
final class FarmHelpView {
    private final Font font;
    private final List<FormattedCharSequence> lines = new ArrayList<>();
    private final int x, y, width, height;
    private int offset;
    private static final int LINE_HEIGHT = 12;

    FarmHelpView(Font font, Component content, int x, int y, int width, int height, int offset) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        for (String paragraph : content.getString().split("\n\n")) {
            if (!lines.isEmpty()) lines.add(FormattedCharSequence.EMPTY);
            for (String row : paragraph.split("\n")) {
                boolean heading = row.startsWith("# ");
                var text = Component.literal(heading ? row.substring(2) : row)
                        .withStyle(style -> style.withColor(heading ? FarmTheme.ACCENT : FarmTheme.TEXT).withBold(heading));
                lines.addAll(font.split(text, width - 28));
            }
        }
        this.offset = Math.clamp(offset, 0, maxOffset());
    }

    int offset() { return offset; }
    int visibleLines() { return Math.max(1, (height - 12) / LINE_HEIGHT); }
    int maxOffset() { return Math.max(0, lines.size() - visibleLines()); }
    void scroll(int delta) { offset = Math.clamp(offset + delta, 0, maxOffset()); }

    boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height || delta == 0) return false;
        scroll(delta > 0 ? -3 : 3);
        return true;
    }

    boolean keyPressed(int key) {
        switch (key) {
            case GLFW.GLFW_KEY_UP -> scroll(-1);
            case GLFW.GLFW_KEY_DOWN -> scroll(1);
            case GLFW.GLFW_KEY_PAGE_UP -> scroll(-visibleLines());
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll(visibleLines());
            case GLFW.GLFW_KEY_HOME -> offset = 0;
            case GLFW.GLFW_KEY_END -> offset = maxOffset();
            default -> { return false; }
        }
        return true;
    }

    void render(GuiGraphics graphics) {
        FarmTheme.panel(graphics, x, y, width, height);
        graphics.enableScissor(x + 4, y + 4, x + width - 4, y + height - 4);
        for (int i = offset; i < Math.min(lines.size(), offset + visibleLines()); i++) {
            graphics.drawString(font, lines.get(i), x + 10, y + 6 + (i - offset) * LINE_HEIGHT, FarmTheme.TEXT, false);
        }
        graphics.disableScissor();
        if (maxOffset() > 0) {
            int track = height - 12;
            int thumb = Math.max(8, track * visibleLines() / lines.size());
            int top = y + 6 + (track - thumb) * offset / maxOffset();
            graphics.fill(x + width - 6, y + 6, x + width - 4, y + height - 6, FarmTheme.LINE);
            graphics.fill(x + width - 6, top, x + width - 4, top + thumb, FarmTheme.ACCENT);
        }
    }
}
