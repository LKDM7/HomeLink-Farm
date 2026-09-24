package fr.lkdm.homelink.farm.network;

import net.minecraft.server.level.ServerPlayer;

/** A block entity accepting commands from its screen, after {@link DeviceCommandPayload} validation. */
public interface DeviceCommandTarget {
    /** Runs on the server thread with an authenticated, authorized player. */
    void handleCommand(ServerPlayer player, DeviceCommand command, int argument);
}
