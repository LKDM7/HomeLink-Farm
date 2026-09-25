package fr.lkdm.homelink.farm.farm.bot;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Contract between a FarmBot and its station. The robot only knows this interface: where to
 * dock, whether it may work, which Crop Monitor feeds it, and where its harvest goes. What the
 * station then does with the items (output inventory, storage port...) is none of its business.
 * Server thread only.
 */
public interface FarmBotHome {
    /** Stable identity of the station. */
    UUID homeId();

    /** Block the robot parks on, in front of the station. */
    BlockPos dockPos();

    /** Direction the robot faces once docked: away from the station, its rear charging plate against it. */
    Direction dockFacing();

    /** Whether this station claims the given robot (one robot per station). */
    boolean owns(UUID robot);

    /** START / PAUSE: false while the player paused the robot. */
    boolean working();

    /** Returns true once when RETURN HOME was requested since the last call. */
    boolean consumeReturnRequest();

    /** The Crop Monitor whose scan results the robot works from, if chosen and loaded. */
    Optional<CropMonitorBlockEntity> cropSource(ServerLevel level);

    /**
     * Stores harvested items.
     * @return what did not fit (never discarded by the station; the robot keeps it)
     */
    ItemStack acceptHarvest(ItemStack stack);

    /** Latest robot state, for the station screen and HomeCore. */
    void report(FarmBotSnapshot snapshot);

    /** Where the robot currently is (lets the station tell an unloaded robot from a vanished one). */
    void locate(BlockPos position);

    /** The robot left for good (picked up or destroyed): the station becomes free. */
    void releaseRobot(UUID robot);
}
