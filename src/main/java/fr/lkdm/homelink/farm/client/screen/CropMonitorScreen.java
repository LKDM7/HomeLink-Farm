package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.client.ClientFarmData;
import fr.lkdm.homelink.farm.farm.controller.ControllerLink;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemCounts;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import fr.lkdm.homelink.farm.network.LocateProblemPayload;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

/** Overview (figures + zone buttons) and Diagnostic (problem list with LOCATE) views. */
public class CropMonitorScreen extends FarmDeviceScreen<CropMonitorBlockEntity> {
    @Override
    protected Component helpContent() {
        return Component.translatable("gui.homelink_farm.help.monitor");
    }

    private static final int ROW_HEIGHT = 17;

    private boolean diagnostic;
    private fr.lkdm.homelink.farm.farm.crop.ComparatorMode lastMode;
    private int page;
    private List<CropProblem> displayedProblems = List.of();
    private ProblemCounts displayedCounts = ProblemCounts.NONE;

    public CropMonitorScreen(FarmDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, CropMonitorBlockEntity.class, 226);
    }

    @Override
    protected int buttonsTop() {
        return diagnostic ? bottomRow() : bottomRow() - 2 * BUTTON_ROW;
    }

    private ProblemCounts counts() {
        return device().flatMap(CropMonitorBlockEntity::result).map(CropScanResult::problems).orElse(ProblemCounts.NONE);
    }

    @Override
    protected void addDeviceWidgets() {
        lastMode = device().map(CropMonitorBlockEntity::comparatorMode).orElse(null);
        displayedProblems = ClientFarmData.problems(menu.pos());
        displayedCounts = counts();
        int bottom = bottomRow();
        if (!diagnostic) {
            commandButton(Component.translatable("gui.homelink_farm.zone.auto"), 10, bottom, 80, DeviceCommand.ZONE_AUTO, 0);
            commandButton(Component.translatable("gui.homelink_farm.zone.clear"), 94, bottom, 80, DeviceCommand.ZONE_CLEAR, 0);
            commandButton(Component.translatable("gui.homelink_farm.rescan"), 178, bottom, 82, DeviceCommand.RESCAN, 0);
            button(Component.translatable("gui.homelink_farm.diagnostic.open"), 10, bottom - 2 * BUTTON_ROW, 123, () -> switchView(true));
            overlayButton(137, bottom - 2 * BUTTON_ROW, 123);
            Component mode = device().map(monitor -> monitor.comparatorMode().label()).orElse(Component.empty());
            commandButton(Component.translatable("gui.homelink_farm.comparator", mode), 10, bottom - BUTTON_ROW, 170, DeviceCommand.CYCLE_COMPARATOR, 0);
            button(Component.translatable("gui.homelink_farm.zone.show"), 184, bottom - BUTTON_ROW, 76, this::showZone);
            return;
        }
        // Problem rows start right under the summary panel and fill the space above the buttons.
        List<Line> summary = new java.util.ArrayList<>();
        collectDiagnostic(summary);
        int rowsTop = PANEL_TOP + summary.size() * LINE_HEIGHT + 7 + 5;
        int rows = Math.max(1, (buttonsTop() - 4 - rowsTop) / ROW_HEIGHT);
        List<CropProblem> problems = ClientFarmData.problems(menu.pos());
        int pages = Math.max(1, (problems.size() + rows - 1) / rows);
        page = Math.min(page, pages - 1);
        for (int row = 0; row < rows; row++) {
            int index = page * rows + row;
            if (index >= problems.size()) break;
            CropProblem problem = problems.get(index);
            Component label = Component.translatable("gui.homelink_farm.diagnostic.row", problem.type().label(),
                    problem.pos().getX(), problem.pos().getY(), problem.pos().getZ());
            button(label, 10, rowsTop + row * ROW_HEIGHT, imageWidth - 20, ROW_HEIGHT - 1, () -> locate(problem));
        }
        Button previous = button(Component.literal("<"), 10, bottom, 30, () -> changePage(-1));
        Button next = button(Component.literal(">"), 44, bottom, 30, () -> changePage(1));
        previous.active = page > 0;
        next.active = page < pages - 1;
        button(Component.translatable("gui.homelink_farm.diagnostic.back"), 180, bottom, 80, () -> switchView(false));
    }

    /** Outlines the monitored zone in the world for a few seconds (client only) and closes the screen. */
    private void showZone() {
        if (device().flatMap(CropMonitorBlockEntity::zone).isEmpty()) return;
        fr.lkdm.homelink.farm.client.rendering.ZonePreview.showMonitorZone(menu.pos());
        onClose();
    }

    private void switchView(boolean showDiagnostic) {
        diagnostic = showDiagnostic;
        page = 0;
        rebuildWidgets();
    }

    private void changePage(int delta) {
        page += delta;
        rebuildWidgets();
    }

    private void locate(CropProblem problem) {
        PacketDistributor.sendToServer(new LocateProblemPayload(menu.pos(), problem.pos()));
        onClose();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (isHelpOpen()) return;
        // Refresh only when the asynchronous data changes; never rebuild while rendering.
        if (diagnostic) {
            if (!displayedProblems.equals(ClientFarmData.problems(menu.pos())) || !displayedCounts.equals(counts())) rebuildWidgets();
        } else if (device().map(CropMonitorBlockEntity::comparatorMode).orElse(null) != lastMode) {
            rebuildWidgets();
        }
    }

    @Override
    protected void collectLines(CropMonitorBlockEntity monitor, List<Line> lines) {
        if (diagnostic) {
            collectDiagnostic(lines);
            return;
        }
        Component controller = monitor.controllerLink().map(this::describe)
                .orElse(Component.translatable("gui.homelink_farm.not_linked"));
        lines.add(line("gui.homelink_farm.controller", controller));
        lines.add(line("gui.homelink_farm.zone", monitor.zone()
                .map(zone -> Component.translatable("gui.homelink_farm.zone.size", zone.sizeX(), zone.sizeY(), zone.sizeZ(), zone.volume()))
                .orElse(Component.translatable("gui.homelink_farm.zone.not_configured")), monitor.zone().isPresent() ? TEXT : WARN));
        if (monitor.zone().isEmpty()) return;
        if (monitor.result().isEmpty()) {
            lines.add(line("gui.homelink_farm.scan", Component.translatable("gui.homelink_farm.scan.first_pass"), WARN));
            return;
        }
        CropScanResult result = monitor.result().get();
        lines.add(line("gui.homelink_farm.scan", result.complete()
                ? Component.translatable("gui.homelink_farm.scan.complete")
                : Component.translatable("gui.homelink_farm.scan.partial", result.unloaded()), result.complete() ? GOOD : WARN));
        lines.add(line("gui.homelink_farm.crops", number(result.crops())));
        lines.add(line("gui.homelink_farm.ready", Component.translatable("gui.homelink_farm.count_percent",
                result.ready(), percent(result.readyFraction())), GOOD));
        lines.add(line("gui.homelink_farm.growing", number(result.growing())));
        lines.add(bar("gui.homelink_farm.maturity", result.maturity(), result.maturity() >= 0.9F ? GOOD : WARN));
        lines.add(irrigation(result.irrigated(), result.irrigable()));
        int problems = result.problems().total();
        lines.add(line("gui.homelink_farm.problems", number(problems), problems == 0 ? GOOD : BAD));
    }

    private void collectDiagnostic(List<Line> lines) {
        ProblemCounts counts = counts();
        lines.add(line("gui.homelink_farm.problems", number(counts.total()), counts.total() == 0 ? GOOD : BAD));
        for (ProblemType type : ProblemType.values()) {
            if (counts.get(type) > 0) {
                lines.add(new Line(Component.literal("  ").append(type.label()), Component.literal(counts.get(type) + " ×"), type.color(), -1));
            }
        }
        if (counts.total() == 0) lines.add(line("gui.homelink_farm.diagnostic.none", Component.empty(), GOOD));
    }

    private Component describe(ControllerLink link) {
        if (minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(link.controllerPos()) instanceof FarmControllerBlockEntity controller
                && controller.deviceId().equals(link.controllerId())) {
            return controller.displayName();
        }
        var pos = link.controllerPos();
        return Component.translatable("gui.homelink_farm.linked_at", pos.getX(), pos.getY(), pos.getZ());
    }
}
