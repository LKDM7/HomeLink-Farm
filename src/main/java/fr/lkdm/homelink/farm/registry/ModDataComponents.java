package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, HomeLinkFarm.MOD_ID);

    /** Farm Controller currently selected by a Farm Connector (dimension + position). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> SELECTED_CONTROLLER =
            DATA_COMPONENTS.registerComponentType("selected_controller",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** First corner (Position A) of a Crop Monitor zone being selected with a Farm Connector. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> ZONE_CORNER_A =
            DATA_COMPONENTS.registerComponentType("zone_corner_a",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    /** Second corner (Position B) of a Crop Monitor zone being selected with a Farm Connector. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GlobalPos>> ZONE_CORNER_B =
            DATA_COMPONENTS.registerComponentType("zone_corner_b",
                    builder -> builder.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));

    private ModDataComponents() {
    }
}
