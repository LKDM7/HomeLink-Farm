package fr.lkdm.homelink.farm.farm.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Empties the FarmBot Station output into an adjacent HomeCore INPUT or BOTH item port.
 * The target is reached through the face it shows to the station, so its own face
 * rules apply (a Deposit touched by its screen or its bottom accepts nothing).
 */
public final class StationOutputTransfer {
    private StationOutputTransfer() {
    }

    /** Moves as much of {@code output} as the adjacent targets accept; returns the number of items moved. */
    public static int push(ServerLevel level, BlockPos station, IItemHandler output) {
        int moved = 0;
        for (Direction side : Direction.values()) {
            BlockPos pos = station.relative(side);
            // Never loads a chunk: a target across an unloaded border simply waits.
            if (!level.isLoaded(pos)) continue;
            var target = level.getCapability(fr.lkdm.homecore.api.item.ItemApi.BLOCK, pos, side.getOpposite());
            if (target == null || !target.type().canReceive()) continue;
            for (int slot = 0; slot < output.getSlots(); slot++) {
                ItemStack stack = output.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                ItemStack rest = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
                int count = stack.getCount() - rest.getCount();
                if (count > 0) {
                    output.extractItem(slot, count, false);
                    moved += count;
                }
            }
        }
        return moved;
    }
}
