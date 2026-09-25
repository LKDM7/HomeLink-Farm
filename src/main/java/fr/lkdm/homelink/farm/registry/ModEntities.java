package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, HomeLinkFarm.MOD_ID);

    /** About 0.8 block wide and half a block tall: low enough to drive between crop rows. */
    public static final DeferredHolder<EntityType<?>, EntityType<FarmBotEntity>> FARMBOT = ENTITY_TYPES.register("farmbot",
            () -> EntityType.Builder.<FarmBotEntity>of(FarmBotEntity::new, MobCategory.MISC)
                    .sized(0.8F, 0.5F)
                    .eyeHeight(0.4F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build(HomeLinkFarm.id("farmbot").toString()));

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(FARMBOT.get(), FarmBotEntity.createAttributes().build());
    }

    private ModEntities() {
    }
}
