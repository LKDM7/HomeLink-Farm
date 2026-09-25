package fr.lkdm.homelink.farm.farm.bot;

import fr.lkdm.homelink.farm.farm.crop.CropAdapter;
import fr.lkdm.homelink.farm.farm.crop.CropAdapters;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Harvests one crop the way its {@link CropAdapter} describes: nothing here knows wheat from
 * carrots. The block is re-read on the server at the moment of harvest, so a crop that was
 * already picked or is no longer mature is left alone.
 */
public final class CropHarvester {
    private CropHarvester() {
    }

    /** Whether the block at {@code pos} is a mature crop a machine can harvest right now. */
    public static boolean harvestable(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) return false;
        BlockState state = level.getBlockState(pos);
        CropAdapter adapter = CropAdapters.get(state);
        return adapter != null && adapter.isMature(state) && adapter.harvestMode(state) != CropAdapter.HarvestMode.NONE;
    }

    /**
     * Harvests the crop at {@code pos}. For a replanted crop one replant item is taken from the
     * drops first, then from {@code inventory}; without any, the spot is left empty.
     * @return the items to store (empty list possible), or empty when nothing was harvested
     */
    public static Optional<List<ItemStack>> harvest(ServerLevel level, BlockPos pos, @Nullable Entity harvester, IItemHandler inventory) {
        if (!harvestable(level, pos)) return Optional.empty();
        BlockState state = level.getBlockState(pos);
        CropAdapter adapter = CropAdapters.get(state);
        List<ItemStack> drops = new ArrayList<>();
        for (ItemStack drop : adapter.harvestDrops(level, pos, state, harvester)) {
            if (!drop.isEmpty()) drops.add(drop.copy());
        }
        if (adapter.harvestMode(state) == CropAdapter.HarvestMode.KEEP_PLANT) {
            level.setBlock(pos, adapter.harvestedState(state), Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(harvester, state));
            return Optional.of(drops);
        }
        ItemStack seed = adapter.replantItem(level, pos, state);
        BlockState replant = adapter.replantState(state);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        level.removeBlock(pos, false);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(harvester, state));
        if (!seed.isEmpty() && replant.canSurvive(level, pos) && (takeOne(drops, seed) || takeOne(inventory, seed))) {
            level.setBlock(pos, replant, Block.UPDATE_ALL);
            level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(harvester, replant));
        }
        drops.removeIf(ItemStack::isEmpty);
        return Optional.of(drops);
    }

    private static boolean takeOne(List<ItemStack> stacks, ItemStack wanted) {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty() && ItemStack.isSameItem(stack, wanted)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    private static boolean takeOne(IItemHandler inventory, ItemStack wanted) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (ItemStack.isSameItem(inventory.getStackInSlot(slot), wanted) && !inventory.extractItem(slot, 1, false).isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
