package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homecore.api.network.HomeNetwork;
import fr.lkdm.homecore.api.network.NetworkMember;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Shared {@link NetworkMember} behaviour of the Farm devices: the binding lives in the backing block entity,
 * with the same rights as the device's own HomeLink button. A view that is not a block cannot be moved.
 */
interface FarmNetworkMember extends NetworkMember {
    /** @return the view this device reads */
    Object source();

    private Optional<AbstractFarmDeviceBlockEntity> block() {
        return source() instanceof AbstractFarmDeviceBlockEntity block && block.exposedToHomeCore() ? Optional.of(block) : Optional.empty();
    }

    @Override default Optional<UUID> homeNetwork() { return block().flatMap(AbstractFarmDeviceBlockEntity::homeNetwork); }
    @Override default Optional<UUID> owner() { return block().flatMap(AbstractFarmDeviceBlockEntity::owner); }
    @Override default boolean canConfigure(ServerPlayer player) { return block().filter(block -> FarmAccess.canManage(player, block)).isPresent(); }
    @Override default void homeNetworkChanged(Optional<HomeNetwork> network) {
        block().ifPresent(block -> network.ifPresentOrElse(value -> block.setHomeNetwork(value.id(), value.name()), block::clearHomeNetwork));
    }
}
