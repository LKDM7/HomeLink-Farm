package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmComponentKind;
import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class FarmControllerScreen extends FarmDeviceScreen<FarmControllerBlockEntity> {
    @Override
    protected Component helpContent() {
        return Component.translatable("gui.homelink_farm.help.controller");
    }

    public FarmControllerScreen(FarmDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, FarmControllerBlockEntity.class, 204);
    }

    @Override
    protected void addDeviceWidgets() {
        commandButton(Component.translatable("gui.homelink_farm.rescan"), 10, bottomRow(), 80, DeviceCommand.RESCAN, 0);
        networkButton(94, bottomRow(), 166);
    }

    @Override
    protected HeaderStatus headerStatus(FarmControllerBlockEntity controller) {
        return new HeaderStatus(Component.translatable("gui.homelink_farm.status.online"), GOOD);
    }

    @Override
    protected void collectLines(FarmControllerBlockEntity controller, List<Line> lines) {
        FarmSummary summary = controller.summary();
        lines.add(line("gui.homelink_farm.status", Component.translatable("gui.homelink_farm.status.online"), GOOD));
        lines.add(line("gui.homelink_farm.owner", Component.literal(controller.ownerName().isEmpty() ? "-" : controller.ownerName())));
        lines.add(line("gui.homelink_farm.crop_areas", Component.literal(summary.cropAreas() + " / "
                + controller.linkedComponents().count(FarmComponentKind.CROP_MONITOR))));
        lines.add(line("gui.homelink_farm.crops", number(summary.crops())));
        lines.add(bar("gui.homelink_farm.ready", summary.readyFraction(), GOOD));
        lines.add(bar("gui.homelink_farm.maturity", summary.maturity(), summary.maturity() >= 0.9F ? GOOD : WARN));
        lines.add(irrigation(summary.irrigated(), summary.irrigable()));
        lines.add(line("gui.homelink_farm.pumps", Component.literal(Integer.toString(summary.pumps())),
                summary.pumpFaults() > 0 ? BAD : TEXT));
        lines.add(line("gui.homelink_farm.pump.sprinklers", Component.translatable("gui.homelink_farm.sprinklers_capacity",
                summary.sprinklers(), summary.capacity()), summary.sprinklers() > summary.capacity() ? BAD : TEXT));
        int problems = summary.problemTotal();
        lines.add(line("gui.homelink_farm.problems", number(problems), problems == 0 ? GOOD : BAD));
        if (summary.stale() > 0) {
            lines.add(line("gui.homelink_farm.data", Component.translatable("gui.homelink_farm.data.stale", summary.stale()), WARN));
        } else if (summary.unavailable() > 0 || summary.partial()) {
            lines.add(line("gui.homelink_farm.data", Component.translatable("gui.homelink_farm.data.partial", summary.unavailable()), WARN));
        }
    }
}
