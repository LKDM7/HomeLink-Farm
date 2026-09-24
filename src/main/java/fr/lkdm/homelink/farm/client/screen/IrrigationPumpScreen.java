package fr.lkdm.homelink.farm.client.screen;

import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.irrigation.PumpSnapshot;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import fr.lkdm.homelink.farm.network.DeviceCommand;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class IrrigationPumpScreen extends FarmDeviceScreen<IrrigationPumpBlockEntity> {
    public IrrigationPumpScreen(FarmDeviceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, IrrigationPumpBlockEntity.class, 215);
    }

    @Override
    protected void addDeviceWidgets() {
        networkButton(10, buttonsTop(), 150);
        commandButton(Component.translatable("gui.homelink_farm.pump.toggle"), 164, buttonsTop(), 96, DeviceCommand.TOGGLE_ENABLED, 0);
        commandButton(Component.translatable("gui.homelink_farm.pump.redstone_cycle"), 10, bottomRow(), 150, DeviceCommand.CYCLE_REDSTONE, 0);
        button(Component.translatable("gui.homelink_farm.overlay.toggle"), 164, bottomRow(), 96,
                fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay::toggle);
    }

    @Override
    protected int buttonsTop() {
        return bottomRow() - BUTTON_ROW;
    }

    @Override
    protected HeaderStatus headerStatus(IrrigationPumpBlockEntity pump) {
        var status = pump.snapshot().status();
        return new HeaderStatus(status.label(), FarmTheme.pumpStatus(status));
    }

    @Override
    protected void collectLines(IrrigationPumpBlockEntity pump, List<Line> lines) {
        PumpSnapshot snapshot = pump.snapshot();
        lines.add(line("gui.homelink_farm.status", snapshot.status().label(), FarmTheme.pumpStatus(snapshot.status())));
        lines.add(line("gui.homelink_farm.pump.enabled", Component.translatable(pump.enabled() ? "gui.homelink_farm.yes" : "gui.homelink_farm.no"),
                pump.enabled() ? GOOD : WARN));
        lines.add(line("gui.homelink_farm.pump.redstone", pump.redstoneMode().label(), TEXT));
        lines.add(line("gui.homelink_farm.pump.water", Component.translatable(snapshot.water() ? "gui.homelink_farm.yes" : "gui.homelink_farm.no"),
                snapshot.water() ? GOOD : BAD));
        // Capacity counts supplying pumps only: a stopped pump shows "n / 0" in the warning color.
        int color = snapshot.overCapacity() ? BAD : snapshot.status() == fr.lkdm.homelink.farm.farm.irrigation.PumpStatus.ACTIVE ? GOOD : WARN;
        lines.add(line("gui.homelink_farm.pump.sprinklers", Component.literal(snapshot.sprinklers() + " / " + snapshot.capacity()), color));
        lines.add(line("gui.homelink_farm.pump.irrigated", number(snapshot.irrigatedCrops()), snapshot.irrigatedCrops() > 0 ? GOOD : TEXT));
        lines.add(line("gui.homelink_farm.pump.pipes", number(snapshot.pipes())));
        lines.add(line("gui.homelink_farm.pump.pumps", number(snapshot.pumps())));
        if (snapshot.incomplete()) {
            lines.add(line("gui.homelink_farm.data", Component.translatable("gui.homelink_farm.pump.incomplete"), WARN));
        }
    }
}
