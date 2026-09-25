package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, HomeLinkFarm.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.homelink_farm"))
            .icon(() -> ModItems.FARM_CONTROLLER.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                output.accept(ModItems.FARM_CONTROLLER.get());
                output.accept(ModItems.CROP_MONITOR.get());
                output.accept(ModItems.FARM_CONNECTOR.get());
                output.accept(ModItems.IRRIGATION_PUMP.get());
                output.accept(ModItems.COPPER_SPRINKLER.get());
                output.accept(ModItems.FARMBOT_STATION.get());
                output.accept(ModItems.FARMBOT.get());
                ModItems.PIPES.forEach(pipe -> output.accept(pipe.get()));
            })
            .build());

    private ModCreativeTabs() {
    }
}
