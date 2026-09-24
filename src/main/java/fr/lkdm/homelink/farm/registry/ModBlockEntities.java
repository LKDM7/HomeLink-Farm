package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CopperSprinklerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, HomeLinkFarm.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FarmControllerBlockEntity>> FARM_CONTROLLER =
            BLOCK_ENTITY_TYPES.register("farm_controller", () -> BlockEntityType.Builder
                    .of(FarmControllerBlockEntity::new, ModBlocks.FARM_CONTROLLER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CropMonitorBlockEntity>> CROP_MONITOR =
            BLOCK_ENTITY_TYPES.register("crop_monitor", () -> BlockEntityType.Builder
                    .of(CropMonitorBlockEntity::new, ModBlocks.CROP_MONITOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<IrrigationPumpBlockEntity>> IRRIGATION_PUMP =
            BLOCK_ENTITY_TYPES.register("irrigation_pump", () -> BlockEntityType.Builder
                    .of(IrrigationPumpBlockEntity::new, ModBlocks.IRRIGATION_PUMP.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CopperSprinklerBlockEntity>> COPPER_SPRINKLER =
            BLOCK_ENTITY_TYPES.register("copper_sprinkler", () -> BlockEntityType.Builder
                    .of(CopperSprinklerBlockEntity::new, ModBlocks.COPPER_SPRINKLER.get()).build(null));

    private ModBlockEntities() {
    }
}
