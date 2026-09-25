package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.menu.FarmBotStationMenu;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, HomeLinkFarm.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<FarmDeviceMenu>> FARM_CONTROLLER = MENUS.register("farm_controller",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> FarmDeviceMenu.client(ModMenus.FARM_CONTROLLER.get(), id, inventory, buf)));

    public static final DeferredHolder<MenuType<?>, MenuType<FarmDeviceMenu>> CROP_MONITOR = MENUS.register("crop_monitor",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> FarmDeviceMenu.client(ModMenus.CROP_MONITOR.get(), id, inventory, buf)));

    public static final DeferredHolder<MenuType<?>, MenuType<FarmDeviceMenu>> IRRIGATION_PUMP = MENUS.register("irrigation_pump",
            () -> IMenuTypeExtension.create((id, inventory, buf) -> FarmDeviceMenu.client(ModMenus.IRRIGATION_PUMP.get(), id, inventory, buf)));

    public static final DeferredHolder<MenuType<?>, MenuType<FarmDeviceMenu>> FARMBOT_STATION = MENUS.register("farmbot_station",
            () -> IMenuTypeExtension.create(FarmBotStationMenu::client));

    private ModMenus() {
    }
}
