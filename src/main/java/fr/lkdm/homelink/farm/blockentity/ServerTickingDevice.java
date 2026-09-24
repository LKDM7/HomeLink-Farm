package fr.lkdm.homelink.farm.blockentity;

import net.minecraft.server.level.ServerLevel;

/** Block entity ticked on the logical server only. */
public interface ServerTickingDevice {
    void serverTick(ServerLevel level);
}
