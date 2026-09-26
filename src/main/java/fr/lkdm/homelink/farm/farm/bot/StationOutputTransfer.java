package fr.lkdm.homelink.farm.farm.bot;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Empties the FarmBot Station output into a storage input placed against the station: any block of
 * the tag {@code homelink_farm:farmbot_station_outputs} (the HomeLink Storage Deposit when that mod
 * is installed). The target is reached through the face it shows to the station, so its own face
 * rules apply (a Deposit touched by its screen or its bottom accepts nothing).
 */
public final class StationOutputTransfer {
    public static final TagKey<Block> TARGETS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(HomeLinkFarm.MOD_ID, "farmbot_station_outputs"));

    private StationOutputTransfer() {
    }

    /** Moves as much of {@code output} as the adjacent targets accept; returns the number of items moved. */
    public static int push(ServerLevel level, BlockPos station, IItemHandler output) {
        int moved = 0;
        for (Direction side : Direction.values()) {
            BlockPos pos = station.relative(side);
            // Never loads a chunk: a target across an unloaded border simply waits.
            if (!level.isLoaded(pos) || !level.getBlockState(pos).is(TARGETS)) continue;
            IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side.getOpposite());
            if (target == null) continue;
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
