package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homelink.farm.farm.irrigation.PumpSnapshot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** View of an Irrigation Pump consumed by its HomeCore device adapter. */
public interface PumpView {
    UUID deviceId();

    Component displayName();

    boolean isOperational();

    Optional<BlockPos> devicePosition();

    Optional<ResourceKey<Level>> deviceDimension();

    PumpSnapshot snapshot();

    boolean enabled();

    void setEnabled(boolean enabled);
}
