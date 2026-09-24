package fr.lkdm.homelink.farm;

import com.mojang.logging.LogUtils;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.homelink.HomeCoreIntegration;
import fr.lkdm.homelink.farm.network.ModPayloads;
import fr.lkdm.homelink.farm.registry.ModBlockEntities;
import fr.lkdm.homelink.farm.registry.ModBlocks;
import fr.lkdm.homelink.farm.registry.ModCreativeTabs;
import fr.lkdm.homelink.farm.registry.ModDataComponents;
import fr.lkdm.homelink.farm.registry.ModItems;
import fr.lkdm.homelink.farm.registry.ModMenus;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationGrowth;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/** Common entry point of HomeLink Farm, the agricultural module of the HomeLink ecosystem. */
@Mod(HomeLinkFarm.MOD_ID)
public final class HomeLinkFarm {
    public static final String MOD_ID = "homelink_farm";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HomeLinkFarm(IEventBus modBus, ModContainer container) {
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITY_TYPES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModDataComponents.DATA_COMPONENTS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        container.registerConfig(ModConfig.Type.SERVER, FarmServerConfig.SPEC);
        modBus.addListener(ModPayloads::register);
        modBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(HomeLinkFarm::onLevelUnload);
        NeoForge.EVENT_BUS.addListener(HomeLinkFarm::onLevelTick);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) IrrigationGrowth.tick(level);
    }

    private static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            IrrigationManager.forget(level);
            fr.lkdm.homelink.farm.farm.crop.LoadedMonitors.forget(level);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(HomeCoreIntegration::registerProviders);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
