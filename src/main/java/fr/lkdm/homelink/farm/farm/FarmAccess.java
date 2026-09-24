package fr.lkdm.homelink.farm.farm;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side authorization for configuring a farm device: its owner (the player who placed
 * it), operators (permission level 2), and members holding HomeCore's CONFIGURE permission
 * on the HomeNetwork the device is attached to.
 */
public final class FarmAccess {
    public static final int OPERATOR_LEVEL = 2;

    private FarmAccess() {
    }

    public static boolean canManage(ServerPlayer player, AbstractFarmDeviceBlockEntity device) {
        if (player.hasPermissions(OPERATOR_LEVEL)) return true;
        if (device.owner().map(owner -> owner.equals(player.getUUID())).orElse(true)) return true;
        return device.homeNetwork().map(network -> DashboardAPI.hasPermission(player, network, Permission.CONFIGURE)).orElse(false);
    }
}
