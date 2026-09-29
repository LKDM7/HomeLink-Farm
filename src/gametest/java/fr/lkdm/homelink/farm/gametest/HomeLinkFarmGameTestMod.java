package fr.lkdm.homelink.farm.gametest;

import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** Development-only mod hosting HomeLink Farm GameTests; excluded from the release JAR. */
@Mod(HomeLinkFarmGameTestMod.MOD_ID)
public final class HomeLinkFarmGameTestMod {
    public static final String MOD_ID = "homelink_farm_gametest";

    public HomeLinkFarmGameTestMod(net.neoforged.bus.api.IEventBus bus) {
        bus.addListener((net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) ->
                event.registerBlockEntity(fr.lkdm.homecore.api.item.ItemApi.BLOCK,
                        net.minecraft.world.level.block.entity.BlockEntityType.DROPPER,
                        (entity, side) -> fr.lkdm.homecore.api.item.ItemApi.of(
                                new net.neoforged.neoforge.items.wrapper.InvWrapper(entity), fr.lkdm.homecore.api.item.ItemPortType.INPUT)));
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> FreePower.enable());
    }
}
