package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.item.FarmConnectorItem;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(HomeLinkFarm.MOD_ID);

    public static final DeferredItem<BlockItem> FARM_CONTROLLER = ITEMS.registerSimpleBlockItem(ModBlocks.FARM_CONTROLLER);
    public static final DeferredItem<BlockItem> CROP_MONITOR = ITEMS.registerSimpleBlockItem(ModBlocks.CROP_MONITOR);
    public static final DeferredItem<BlockItem> IRRIGATION_PUMP = ITEMS.registerSimpleBlockItem(ModBlocks.IRRIGATION_PUMP);
    public static final DeferredItem<BlockItem> COPPER_SPRINKLER = ITEMS.registerSimpleBlockItem(ModBlocks.COPPER_SPRINKLER);
    public static final List<DeferredItem<BlockItem>> PIPES = ModBlocks.PIPES.stream()
            .map(ITEMS::registerSimpleBlockItem).toList();
    public static final DeferredItem<FarmConnectorItem> FARM_CONNECTOR = ITEMS.register("farm_connector",
            () -> new FarmConnectorItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
