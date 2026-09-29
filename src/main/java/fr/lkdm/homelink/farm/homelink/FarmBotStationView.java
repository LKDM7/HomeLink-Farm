package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homelink.farm.farm.bot.FarmBotSnapshot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/** View of a FarmBot Station consumed by its HomeCore device adapter. */
public interface FarmBotStationView {
    UUID deviceId();

    Component displayName();

    /** @param name name chosen by a player; empty restores the default name */
    void setCustomName(String name);

    boolean isOperational();

    Optional<BlockPos> devicePosition();

    Optional<ResourceKey<Level>> deviceDimension();

    /** Latest robot report; empty when no robot is installed. */
    Optional<FarmBotSnapshot> snapshot();

    int outputUsed();

    boolean working();

    void setWorking(boolean working);

    void requestReturn();
}
