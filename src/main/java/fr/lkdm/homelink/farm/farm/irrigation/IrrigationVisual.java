package fr.lkdm.homelink.farm.farm.irrigation;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Client-visible state of pumps and sprinklers (drives their textures and the overlay). */
public enum IrrigationVisual implements StringRepresentable {
    OFF, ACTIVE, ERROR;

    public static final EnumProperty<IrrigationVisual> PROPERTY = EnumProperty.create("status", IrrigationVisual.class);

    public static IrrigationVisual of(NetworkState state) {
        if (state.irrigates()) return ACTIVE;
        return state.isFault() ? ERROR : OFF;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
