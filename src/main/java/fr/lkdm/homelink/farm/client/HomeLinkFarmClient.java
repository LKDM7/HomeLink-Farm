package fr.lkdm.homelink.farm.client;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay;
import fr.lkdm.homelink.farm.client.rendering.LocateMarkers;
import fr.lkdm.homelink.farm.client.rendering.ZonePreview;
import fr.lkdm.homelink.farm.client.rendering.FarmBotModel;
import fr.lkdm.homelink.farm.client.rendering.FarmBotRenderer;
import fr.lkdm.homelink.farm.client.screen.CropMonitorScreen;
import fr.lkdm.homelink.farm.client.screen.FarmBotStationScreen;
import fr.lkdm.homelink.farm.client.screen.FarmControllerScreen;
import fr.lkdm.homelink.farm.client.screen.IrrigationPumpScreen;
import fr.lkdm.homelink.farm.registry.ModEntities;
import fr.lkdm.homelink.farm.registry.ModMenus;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point: rendering and screens never load on a dedicated server. */
@Mod(value = HomeLinkFarm.MOD_ID, dist = Dist.CLIENT)
public final class HomeLinkFarmClient {
    public HomeLinkFarmClient(IEventBus modBus, ModContainer container) {
        // In-game config screen (Mods > HomeLink Farm > Config), fully translated through the lang files.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        modBus.addListener(HomeLinkFarmClient::registerScreens);
        modBus.addListener(IrrigationOverlay::registerKey);
        modBus.addListener(HomeLinkFarmClient::registerRenderers);
        modBus.addListener(HomeLinkFarmClient::registerLayers);
        NeoForge.EVENT_BUS.addListener(LocateMarkers::onClientTick);
        NeoForge.EVENT_BUS.addListener(LocateMarkers::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(IrrigationOverlay::onClientTick);
        NeoForge.EVENT_BUS.addListener(IrrigationOverlay::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(ZonePreview::onRenderLevel);
        NeoForge.EVENT_BUS.addListener(HomeLinkFarmClient::onLogout);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.FARM_CONTROLLER.get(), FarmControllerScreen::new);
        event.register(ModMenus.CROP_MONITOR.get(), CropMonitorScreen::new);
        event.register(ModMenus.IRRIGATION_PUMP.get(), IrrigationPumpScreen::new);
        event.register(ModMenus.FARMBOT_STATION.get(), FarmBotStationScreen::new);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.FARMBOT.get(), FarmBotRenderer::new);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FarmBotModel.LAYER, FarmBotModel::createLayer);
    }

    private static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientFarmData.clear();
        LocateMarkers.clear();
        IrrigationOverlay.clear();
        ZonePreview.clear();
    }
}
