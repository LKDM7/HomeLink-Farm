package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** Read-only view of a Farm Controller consumed by its HomeCore device adapter. */
public interface FarmControllerView {
    UUID deviceId();

    Component displayName();

    /** @param name name chosen by a player; empty restores the default name */
    void setCustomName(String name);

    boolean isOperational();

    Optional<BlockPos> devicePosition();

    Optional<ResourceKey<Level>> deviceDimension();

    FarmSummary summary();

    /** Asks every loaded linked Crop Monitor to start a new scan pass. */
    void requestRescan();
}
