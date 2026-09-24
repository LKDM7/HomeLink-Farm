package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homecore.api.DashboardAPI;
import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.security.Permission;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Attaches HomeCore-exposed devices (Farm Controller, Irrigation Pump) to a HomeNetwork.
 * HomeCore's network mutation API is trusted server code, so every player request is checked
 * here: rights on the device AND {@link Permission#MANAGE_NETWORK} on both the new and the
 * previous network.
 */
public final class HomeNetworkBinding {
    public enum Result { BOUND, UNBOUND, UNCHANGED, DENIED, UNKNOWN_NETWORK, NOT_SUPPORTED }

    private HomeNetworkBinding() {
    }

    /** Networks in which this player may add or remove devices. */
    public static List<HomeNetwork> manageableNetworks(ServerPlayer player) {
        return DashboardAPI.networks(player.server).getNetworksForPlayer(player.getUUID()).stream()
                .filter(network -> DashboardAPI.hasPermission(player, network.id(), Permission.MANAGE_NETWORK))
                .toList();
    }

    public static Result bind(ServerPlayer player, AbstractFarmDeviceBlockEntity device, Optional<UUID> target) {
        if (!device.exposedToHomeCore()) return Result.NOT_SUPPORTED;
        if (!FarmAccess.canManage(player, device)) return Result.DENIED;
        Optional<UUID> current = device.homeNetwork();
        if (current.equals(target)) return Result.UNCHANGED;
        var networks = DashboardAPI.networks(player.server);
        Optional<HomeNetwork> destination = Optional.empty();
        if (target.isPresent()) {
            destination = networks.getNetwork(target.get());
            if (destination.isEmpty()) return Result.UNKNOWN_NETWORK;
            if (!DashboardAPI.hasPermission(player, target.get(), Permission.MANAGE_NETWORK)) return Result.DENIED;
        }
        if (current.isPresent() && networks.getNetwork(current.get()).isPresent()) {
            if (!DashboardAPI.hasPermission(player, current.get(), Permission.MANAGE_NETWORK)) return Result.DENIED;
            networks.removeDevice(current.get(), device.deviceId());
        }
        if (destination.isPresent()) {
            networks.addDevice(destination.get().id(), device.deviceId());
            device.setHomeNetwork(destination.get().id(), destination.get().name());
            return Result.BOUND;
        }
        device.clearHomeNetwork();
        return Result.UNBOUND;
    }

    /** A device block was destroyed: remove its identity from its network (trusted server call). */
    public static void forgetOnRemoval(ServerLevel level, AbstractFarmDeviceBlockEntity device) {
        device.homeNetwork().ifPresent(network -> {
            var networks = DashboardAPI.networks(level.getServer());
            if (networks.getNetwork(network).isPresent()) networks.removeDevice(network, device.deviceId());
        });
    }
}
