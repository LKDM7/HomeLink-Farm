package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homecore.api.client.ui.HomeLinkUi;

import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import fr.lkdm.homelink.farm.farm.bot.FarmBotFault;
import fr.lkdm.homelink.farm.farm.bot.FarmBotSnapshot;
import fr.lkdm.homelink.farm.menu.FarmBotStationMenu;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * Station screen. Status view: the robot's state, battery, cargo, target and counters with
 * START / PAUSE, RETURN HOME, the Crop Monitor choice and the HomeLink network. Output view:
 * the nine output slots above the player's inventory.
 */
public class FarmBotStationScreen extends FarmDeviceScreen<FarmBotStationBlockEntity> {
    private boolean outputView;
    private boolean shownWorking;
    private boolean shownRobot;
    private String shownMonitor = "";
    /** Prefix of {@link #shownMonitor} in whole-farm mode (cannot appear in a device name). */
    private static final String WHOLE_FARM = "\u0000farm:";

    public FarmBotStationScreen(FarmDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FarmBotStationBlockEntity.class, 226);
    }

    @Override
    protected Component helpContent() {
        return Component.translatable("gui.homelink_farm.help.farmbot_station");
    }

    @Override
    protected boolean showStatus() {
        return !outputView;
    }

    @Override
    protected int buttonsTop() {
        return outputView ? bottomRow() : bottomRow() - 2 * BUTTON_ROW;
    }

    @Override
    protected void init() {
        super.init();
        // Slots only exist on screen in the output view (never behind the help page).
        ((FarmBotStationMenu) menu).setSlotsVisible(outputView && !isHelpOpen());
    }

    @Override
    protected void addDeviceWidgets() {
        int bottom = bottomRow();
        if (outputView) {
            button(Component.translatable("gui.homelink_farm.farmbot.back"), 10, bottom, 250, () -> switchView(false));
            return;
        }
        shownWorking = device().map(FarmBotStationBlockEntity::working).orElse(true);
        shownRobot = device().map(FarmBotStationBlockEntity::hasRobot).orElse(false);
        shownMonitor = device().map(FarmBotStationScreen::monitorKey).orElse("");
        commandButton(Component.translatable(shownWorking ? "gui.homelink_farm.farmbot.pause" : "gui.homelink_farm.farmbot.start"),
                10, bottom - 2 * BUTTON_ROW, 123, shownWorking ? DeviceCommand.FARMBOT_PAUSE : DeviceCommand.FARMBOT_START, 0);
        Button home = commandButton(Component.translatable("gui.homelink_farm.farmbot.return"), 137, bottom - 2 * BUTTON_ROW, 123,
                DeviceCommand.FARMBOT_RETURN, 0);
        home.active = shownRobot;
        commandButton(Component.translatable("gui.homelink_farm.farmbot.monitor_button", monitorLabel()), 10, bottom - BUTTON_ROW, 170,
                DeviceCommand.CYCLE_MONITOR, 0);
        button(Component.translatable("gui.homelink_farm.farmbot.output"), 184, bottom - BUTTON_ROW, 76, () -> switchView(true));
        networkButton(10, bottom, 250);
    }

    /** What the MONITOR button and line show: the whole farm (with its monitor count), one monitor, or none. */
    private static String monitorKey(FarmBotStationBlockEntity station) {
        return station.wholeFarm() ? WHOLE_FARM + station.farmMonitors() : station.monitorName();
    }

    private Component monitorLabel() {
        if (shownMonitor.startsWith(WHOLE_FARM)) {
            return Component.translatable("gui.homelink_farm.farmbot.whole_farm", shownMonitor.substring(WHOLE_FARM.length()));
        }
        return shownMonitor.isEmpty() ? Component.translatable("gui.homelink_farm.farmbot.no_monitor") : Component.literal(shownMonitor);
    }

    private void switchView(boolean output) {
        outputView = output;
        rebuildWidgets();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (isHelpOpen() || outputView) return;
        boolean working = device().map(FarmBotStationBlockEntity::working).orElse(true);
        boolean robot = device().map(FarmBotStationBlockEntity::hasRobot).orElse(false);
        String monitor = device().map(FarmBotStationScreen::monitorKey).orElse("");
        if (working != shownWorking || robot != shownRobot || !monitor.equals(shownMonitor)) rebuildWidgets();
    }

    @Override
    protected HeaderStatus headerStatus(FarmBotStationBlockEntity station) {
        return station.snapshot()
                .map(robot -> new HeaderStatus(robot.state().label(), FarmStatusColors.farmBotStatus(robot.state())))
                .orElse(new HeaderStatus(Component.translatable("gui.homelink_farm.farmbot.none"), LABEL));
    }

    @Override
    protected void collectLines(FarmBotStationBlockEntity station, List<Line> lines) {
        var robot = station.snapshot();
        if (robot.isEmpty()) {
            lines.add(new Line(Component.translatable("gui.homelink_farm.farmbot.none"), Component.empty(), WARN, -1));
            lines.add(new Line(Component.translatable("gui.homelink_farm.farmbot.insert"), Component.empty(), LABEL, -1));
        } else {
            FarmBotSnapshot bot = robot.get();
            lines.add(line("gui.homelink_farm.farmbot.robot", Component.literal(bot.name())));
            Component status = bot.fault() == FarmBotFault.NONE ? bot.state().label()
                    : Component.translatable("gui.homelink_farm.farmbot.status_fault", bot.state().label(), bot.fault().label());
            lines.add(line("gui.homelink_farm.status", status, FarmStatusColors.farmBotStatus(bot.state())));
            int low = fr.lkdm.homelink.farm.config.FarmServerConfig.FARMBOT_LOW_BATTERY_THRESHOLD.get();
            lines.add(bar("gui.homelink_farm.farmbot.battery", bot.battery() / 100F, bot.battery() <= low ? BAD : bot.battery() < 50 ? WARN : GOOD));
            lines.add(new Line(Component.translatable("gui.homelink_farm.farmbot.storage"),
                    Component.literal(bot.storage() + " / " + FarmBotEntity.INVENTORY_SIZE), bot.storage() >= FarmBotEntity.INVENTORY_SIZE ? WARN : TEXT,
                    bot.storage() / (float) FarmBotEntity.INVENTORY_SIZE));
            lines.add(line("gui.homelink_farm.farmbot.target", bot.targetLabel()));
            lines.add(line("gui.homelink_farm.farmbot.harvested", number(bot.harvested())));
        }
        lines.add(line("gui.homelink_farm.farmbot.monitor", monitorLabel(), shownMonitor.isEmpty() ? WARN : TEXT));
        int used = station.outputUsed();
        lines.add(line("gui.homelink_farm.farmbot.station_output", Component.literal(used + " / " + FarmBotStationBlockEntity.OUTPUT_SIZE),
                used >= FarmBotStationBlockEntity.OUTPUT_SIZE ? BAD : TEXT));
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
        if (!outputView || isHelpOpen()) return;
        for (var slot : menu.slots) {
            if (slot.isActive()) HomeLinkUi.slot(graphics, leftPos + slot.x, topPos + slot.y);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (!outputView || isHelpOpen()) return;
        graphics.drawString(font, Component.translatable("gui.homelink_farm.farmbot.station_output"), FarmBotStationMenu.SLOTS_X,
                FarmBotStationMenu.OUTPUT_Y - 11, LABEL, false);
        graphics.drawString(font, playerInventoryTitle, FarmBotStationMenu.SLOTS_X, FarmBotStationMenu.INVENTORY_Y - 11, LABEL, false);
    }
}
