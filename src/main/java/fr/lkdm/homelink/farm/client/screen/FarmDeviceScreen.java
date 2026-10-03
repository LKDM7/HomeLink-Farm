package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homecore.api.client.ui.HomeLinkTheme;
import fr.lkdm.homecore.api.client.ui.HomeLinkUi;
import fr.lkdm.homecore.api.client.ui.HomeLinkButton;

import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay;
import fr.lkdm.homelink.farm.farm.DeviceNames;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import fr.lkdm.homelink.farm.network.DeviceCommandPayload;
import fr.lkdm.homelink.farm.network.RenameFarmDevicePayload;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Device screen using the shared HomeLink UI: header with status light, rename field,
 * read-only status lines in a recessed panel and optional command buttons at the bottom.
 * Values come from the client copy of the block entity (display only; the server decides).
 */
public abstract class FarmDeviceScreen<T extends AbstractFarmDeviceBlockEntity> extends AbstractContainerScreen<FarmDeviceMenu> {
    protected static final int TEXT = HomeLinkTheme.TEXT;
    protected static final int LABEL = HomeLinkTheme.MUTED;
    protected static final int GOOD = HomeLinkTheme.ONLINE;
    protected static final int WARN = HomeLinkTheme.WARNING;
    protected static final int BAD = HomeLinkTheme.OFFLINE;
    /** Every screen stays under 240 scaled pixels high, the smallest GUI height "auto" scale allows. */
    protected static final int WIDTH = 270;
    protected static final int HEADER_HEIGHT = HomeLinkTheme.HEADER_HEIGHT;
    protected static final int LINE_HEIGHT = 10;
    /** Top of the status panel; its text starts 4 pixels lower. */
    protected static final int PANEL_TOP = 56;
    protected static final int LINES_TOP = PANEL_TOP + 4;
    protected static final int BUTTON_HEIGHT = HomeLinkTheme.CONTROL_HEIGHT;
    /** Vertical distance between two rows of buttons. */
    protected static final int BUTTON_ROW = 21;
    /** Minimum x of the value column; it moves right when a (translated) label is longer. */
    protected static final int VALUE_X = 110;

    /** Status shown at the right of the header: a colored light and a short text. */
    protected record HeaderStatus(Component text, int color) {
    }

    /** One status line; {@code bar} in 0..1 draws a progress bar, negative for none. */
    protected record Line(Component label, Component value, int color, float bar) {
    }

    private final Class<T> deviceClass;
    private EditBox nameBox;
    @org.jetbrains.annotations.Nullable
    private Button networkButton;
    /** True when the current view has an overlay button; its key then works in the screen too. */
    private boolean overlayToggle;
    private boolean helpOpen;
    private FarmHelpView help;
    private Button helpUp, helpDown;

    protected FarmDeviceScreen(FarmDeviceMenu menu, Inventory inventory, Component title, Class<T> deviceClass, int height) {
        super(menu, inventory, title);
        this.deviceClass = deviceClass;
        imageWidth = WIDTH;
        // One more status line for the HomeLink Energy charge of every device.
        imageHeight = height + LINE_HEIGHT;
    }

    @Override
    protected void init() {
        String draft = nameBox == null ? null : nameBox.getValue();
        boolean editingName = nameBox != null && nameBox.isFocused();
        super.init();
        networkButton = null;
        overlayToggle = false;
        Button helpButton = button(Component.translatable("gui.homelink_farm.help.button"), imageWidth - 30, 3, 20, this::toggleHelp);
        ((HomeLinkButton) helpButton).selectedWhen(() -> helpOpen);
        helpButton.setTooltip(Tooltip.create(Component.translatable("gui.homelink_farm.help.tooltip")));
        if (helpOpen) {
            help = new FarmHelpView(font, helpContent(), leftPos + 10, topPos + 43, imageWidth - 20,
                    bottomRow() - 50, help == null ? 0 : help.offset());
            helpUp = button(Component.translatable("gui.homelink_farm.help.up"), 10, bottomRow(), 30,
                    () -> { help.scroll(-help.visibleLines()); refreshHelpButtons(); });
            helpDown = button(Component.translatable("gui.homelink_farm.help.down"), 44, bottomRow(), 30,
                    () -> { help.scroll(help.visibleLines()); refreshHelpButtons(); });
            button(Component.translatable("gui.homelink_farm.help.back"), 82, bottomRow(), 178, this::toggleHelp);
            refreshHelpButtons();
            return;
        }
        nameBox = HomeLinkUi.input(new EditBox(font, leftPos + 10, topPos + 30, 170,
                HomeLinkTheme.CONTROL_HEIGHT, Component.translatable("gui.homelink_farm.name")));
        nameBox.setMaxLength(DeviceNames.MAX_LENGTH);
        if (draft != null) nameBox.setValue(draft);
        else device().ifPresent(device -> nameBox.setValue(device.customName()));
        addRenderableWidget(nameBox);
        if (editingName) setFocused(nameBox);
        button(Component.translatable("gui.homelink_farm.rename"), 184, 29, 76, this::sendRename);
        addDeviceWidgets();
    }

    /** Top of the last row of buttons. */
    protected int bottomRow() {
        return imageHeight - BUTTON_HEIGHT - 6;
    }

    /** Top of the first row of bottom buttons (a divider is drawn just above it). */
    protected int buttonsTop() {
        return bottomRow();
    }

    /** Whether the status panel and its lines are shown (a screen may swap them for another view). */
    protected boolean showStatus() {
        return true;
    }

    /** Adds command buttons; use {@link #leftPos}/{@link #topPos}. */
    protected void addDeviceWidgets() {
    }

    /** Shared HomeLink button at (x, y) relative to the window. */
    protected Button button(Component label, int x, int y, int width, Runnable action) {
        return button(label, x, y, width, BUTTON_HEIGHT, action);
    }

    protected Button button(Component label, int x, int y, int width, int height, Runnable action) {
        return addRenderableWidget(HomeLinkButton.builder(label, pressed -> action.run())
                .bounds(leftPos + x, topPos + y, width, height).build());
    }

    protected Button commandButton(Component label, int x, int y, int width, DeviceCommand command, int argument) {
        return button(label, x, y, width, () -> sendCommand(command, argument));
    }

    /** "Show irrigation" toggle, pressed with copper text while its world overlay is on. */
    protected Button overlayButton(int x, int y, int width) {
        Button button = button(Component.translatable("gui.homelink_farm.overlay.toggle"), x, y, width, IrrigationOverlay::toggle);
        ((HomeLinkButton) button).selectedWhen(IrrigationOverlay::enabled);
        overlayToggle = true;
        return button;
    }

    /**
     * "HomeLink: network" button cycling through the networks the player may manage (as sent by
     * the server) and "none". The server re-checks every permission when it receives the choice.
     */
    protected Button networkButton(int x, int y, int width) {
        networkButton = button(networkLabel(), x, y, width, this::cycleNetwork);
        return networkButton;
    }

    private Component networkLabel() {
        String name = device().filter(device -> device.homeNetwork().isPresent()).map(AbstractFarmDeviceBlockEntity::homeNetworkName)
                .orElse(null);
        return Component.translatable("gui.homelink_farm.network", name == null
                ? Component.translatable("gui.homelink_farm.network.none") : Component.literal(name));
    }

    private void cycleNetwork() {
        var choices = fr.lkdm.homelink.farm.client.ClientFarmData.networkChoices(menu.pos());
        var current = device().flatMap(AbstractFarmDeviceBlockEntity::homeNetwork);
        int index = -1;
        for (int i = 0; i < choices.size(); i++) {
            if (current.isPresent() && choices.get(i).id().equals(current.get())) index = i;
        }
        int next = index + 1;
        java.util.Optional<java.util.UUID> target = next < choices.size() ? java.util.Optional.of(choices.get(next).id()) : java.util.Optional.empty();
        if (choices.isEmpty() && current.isEmpty()) {
            if (minecraft != null && minecraft.player != null) {
                minecraft.player.displayClientMessage(Component.translatable("message.homelink_farm.network.no_choices"), true);
            }
            return;
        }
        PacketDistributor.sendToServer(new fr.lkdm.homelink.farm.network.HomeNetworkPayloads.Bind(menu.pos(), target));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (networkButton != null) {
            Component label = networkLabel();
            if (!label.equals(networkButton.getMessage())) networkButton.setMessage(label);
        }
    }

    protected void sendCommand(DeviceCommand command, int argument) {
        PacketDistributor.sendToServer(new DeviceCommandPayload(menu.pos(), command, argument));
    }

    private void sendRename() {
        PacketDistributor.sendToServer(new RenameFarmDevicePayload(menu.pos(), nameBox.getValue()));
    }

    protected Optional<T> device() {
        if (minecraft == null || minecraft.level == null) return Optional.empty();
        var blockEntity = minecraft.level.getBlockEntity(menu.pos());
        return deviceClass.isInstance(blockEntity) ? Optional.of(deviceClass.cast(blockEntity)) : Optional.empty();
    }

    /** Status lines displayed under the name field. */
    protected abstract void collectLines(T device, List<Line> lines);

    protected abstract Component helpContent();

    public boolean isHelpOpen() { return helpOpen; }

    private void toggleHelp() {
        helpOpen = !helpOpen;
        rebuildWidgets();
    }

    private void refreshHelpButtons() {
        helpUp.active = help.offset() > 0;
        helpDown.active = help.offset() < help.maxOffset();
    }

    /** Header status; by default whether the device is linked (its lights blink in the world). */
    private HeaderStatus headerOrNoPower(T device) {
        return device.energyPort() != null && !device.energized()
                ? new HeaderStatus(Component.translatable("gui.homelink_farm.energy.no_power"), BAD)
                : headerStatus(device);
    }

    protected HeaderStatus headerStatus(T device) {
        return device.isLinked()
                ? new HeaderStatus(Component.translatable("gui.homelink_farm.linked"), GOOD)
                : new HeaderStatus(Component.translatable("gui.homelink_farm.not_linked"), LABEL);
    }

    private List<Line> lines(T device) {
        List<Line> lines = new ArrayList<>();
        collectLines(device, lines);
        if (device.energyPort() != null) lines.add(energyLine(device));
        return lines;
    }

    /** Charge of the device's HomeLink Energy buffer, or NO POWER once it stopped for lack of HE. */
    private Line energyLine(T device) {
        int percent = device.energyPercent();
        Component value = device.energized() || percent > 0
                ? Component.literal(percent + "%")
                : Component.translatable("gui.homelink_farm.energy.none");
        int color = !device.energized() ? BAD : percent < 25 ? WARN : GOOD;
        return new Line(Component.translatable("gui.homelink_farm.energy"), value, color, percent / 100F);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        HomeLinkUi.window(graphics, leftPos, topPos, imageWidth, imageHeight, HEADER_HEIGHT);
        if (helpOpen) {
            help.render(graphics);
            return;
        }
        HomeLinkUi.separator(graphics, leftPos + 10, topPos + 52, imageWidth - 20);
        HomeLinkUi.separator(graphics, leftPos + 10, topPos + buttonsTop() - 4, imageWidth - 20);
        if (!showStatus()) return;
        int count = device().map(device -> lines(device).size()).orElse(1);
        HomeLinkUi.panel(graphics, leftPos + 10, topPos + PANEL_TOP, imageWidth - 20, count * LINE_HEIGHT + 7);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<T> device = device();
        HeaderStatus status = device.map(this::headerOrNoPower)
                .orElse(new HeaderStatus(Component.translatable("gui.homelink_farm.unavailable"), BAD));
        String statusText = font.plainSubstrByWidth(status.text().getString(), 90);
        int statusX = imageWidth - 38 - font.width(statusText);
        HomeLinkUi.statusDot(graphics, statusX - 12, 8, status.color());
        graphics.drawString(font, statusText, statusX, 8, LABEL, false);
        Component heading = device.map(AbstractFarmDeviceBlockEntity::displayName).orElse(title);
        boolean renamed = device.filter(named -> !named.customName().isEmpty()).isPresent();
        graphics.drawString(font, font.plainSubstrByWidth(heading.getString(), statusX - 30), 14, 8,
                renamed ? HomeLinkTheme.ACCENT : TEXT, false);
        if (helpOpen) {
            graphics.drawString(font, Component.translatable("gui.homelink_farm.help.title"), 12, 30, HomeLinkTheme.ACCENT, false);
            return;
        }
        if (!showStatus()) return;
        if (device.isEmpty()) {
            graphics.drawString(font, Component.translatable("gui.homelink_farm.unavailable"), 16, LINES_TOP, BAD, false);
            return;
        }
        List<Line> lines = lines(device.get());
        int valueX = VALUE_X;
        for (Line line : lines) valueX = Math.max(valueX, 16 + font.width(line.label()) + 8);
        int y = LINES_TOP;
        for (Line line : lines) {
            graphics.drawString(font, line.label(), 16, y, LABEL, false);
            int valueWidth = font.width(line.value());
            graphics.drawString(font, line.value(), valueX, y, line.color(), false);
            if (line.bar() >= 0) {
                int barX = valueX + valueWidth + 6;
                HomeLinkUi.gauge(graphics, barX, y + 2, imageWidth - 16 - barX, line.bar(), line.color());
            }
            y += LINE_HEIGHT;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (helpOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                toggleHelp();
                return true;
            }
            if (help.keyPressed(keyCode)) {
                refreshHelpButtons();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (nameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                sendRename();
                return true;
            }
            if (keyCode != GLFW.GLFW_KEY_ESCAPE && keyCode != GLFW.GLFW_KEY_TAB) {
                // Keep typed letters (e.g. the inventory key) from closing the screen.
                return nameBox.keyPressed(keyCode, scanCode, modifiers) || nameBox.canConsumeInput();
            }
        }
        if (overlayToggle && IrrigationOverlay.TOGGLE_KEY.matches(keyCode, scanCode)) {
            IrrigationOverlay.toggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (helpOpen && help.mouseScrolled(mouseX, mouseY, deltaY)) {
            refreshHelpButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    protected static Line line(String labelKey, Component value) {
        return new Line(Component.translatable(labelKey), value, TEXT, -1);
    }

    protected static Line line(String labelKey, Component value, int color) {
        return new Line(Component.translatable(labelKey), value, color, -1);
    }

    protected static Line bar(String labelKey, float fraction, int color) {
        return new Line(Component.translatable(labelKey), percent(fraction), color, fraction);
    }

    /** "irrigated / irrigable (coverage%) +bonus" line; the bonus only shows when something is irrigated. */
    protected static Line irrigation(int irrigated, int irrigable) {
        float coverage = irrigable == 0 ? 0 : irrigated / (float) irrigable;
        Component value = Component.translatable("gui.homelink_farm.irrigation.value", irrigated, irrigable, percent(coverage));
        if (irrigated > 0) value = value.copy().append(Component.translatable("gui.homelink_farm.irrigation.bonus",
                Math.round(fr.lkdm.homelink.farm.farm.irrigation.GrowthBonus.configuredBonus() * 100)));
        int color = irrigable == 0 ? TEXT : coverage >= 0.95F ? GOOD : irrigated > 0 ? WARN : TEXT;
        return new Line(Component.translatable("gui.homelink_farm.irrigation"), value, color, -1);
    }

    /** Percentage with one decimal, written the way the selected language writes numbers (64.3% / 64,3 %). */
    protected static Component percent(float fraction) {
        String code = net.minecraft.client.Minecraft.getInstance().getLanguageManager().getSelected();
        java.util.Locale locale = java.util.Locale.forLanguageTag(code.replace('_', '-'));
        String number = String.format(locale, "%.1f", Math.round(fraction * 1000) / 10.0F);
        return Component.translatable("gui.homelink_farm.percent", number);
    }

    protected static Component number(int value) {
        return Component.literal(Integer.toString(value));
    }
}
