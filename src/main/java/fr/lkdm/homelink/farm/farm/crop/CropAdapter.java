package fr.lkdm.homelink.farm.farm.crop;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Describes how HomeLink Farm reads one family of crops. Public extension point: third-party
 * mods register their own adapters through {@link CropAdapters#register(CropAdapter)}.
 * Implementations must be stateless, cheap and safe to call on the server thread.
 */
public interface CropAdapter {
    /** Whether this adapter understands the given state. Only called once per block (cached). */
    boolean matches(BlockState state);

    /**
     * Whether this particular state should be counted as a crop (e.g. only the lower half of
     * a double-height crop). Defaults to every matching state.
     */
    default boolean isCounted(BlockState state) {
        return true;
    }

    int age(BlockState state);

    int maxAge(BlockState state);

    default boolean isMature(BlockState state) {
        return age(state) >= maxAge(state);
    }

    /** Normalized maturity from 0 to 1. */
    default float maturity(BlockState state) {
        int max = maxAge(state);
        if (max <= 0) return 1.0F;
        return Math.min(1.0F, Math.max(0, age(state)) / (float) max);
    }

    /** Whether the crop grows on farmland (enables the dry-farmland diagnostic). */
    default boolean growsOnFarmland(BlockState state) {
        return false;
    }

    /** Whether HomeLink irrigation may give this crop its growth bonus. */
    default boolean acceptsIrrigation(BlockState state) {
        return false;
    }

    /**
     * Minimum raw light level required by the crop's own random-tick growth, or 0 when the
     * crop has no reliable light requirement.
     */
    default int minimumGrowthLight(BlockState state) {
        return 0;
    }

    /** Where the crop samples light for {@link #minimumGrowthLight}. */
    default BlockPos lightSamplePos(BlockPos pos, BlockState state) {
        return pos;
    }

    /**
     * Applies one extra growth opportunity. The default performs exactly one vanilla random
     * tick, so every vanilla rule (light, farmland, events) still applies.
     */
    default void applyGrowthTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.isRandomlyTicking()) state.randomTick(level, pos, random);
    }

    /** How a machine (the FarmBot) may harvest this crop once it is mature. */
    enum HarvestMode {
        /** Not harvested automatically (stems, multi-block plants...). */
        NONE,
        /** Broken for its drops, then replanted with one {@link #replantItem} taken from the harvest. */
        REPLANT,
        /** The plant stays: its drops are collected and it goes back to {@link #harvestedState}. */
        KEEP_PLANT
    }

    default HarvestMode harvestMode(BlockState state) {
        return HarvestMode.NONE;
    }

    /** Items produced by harvesting this mature crop (its loot table by default). */
    default List<ItemStack> harvestDrops(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity harvester) {
        return Block.getDrops(state, level, pos, level.getBlockEntity(pos), harvester, ItemStack.EMPTY);
    }

    /** Item consumed to replant a {@link HarvestMode#REPLANT} crop (its pick-block item by default, e.g. seeds). */
    default ItemStack replantItem(LevelReader level, BlockPos pos, BlockState state) {
        return state.getCloneItemStack(new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false), level, pos, null);
    }

    /** State placed back when replanting (the youngest stage). */
    default BlockState replantState(BlockState state) {
        return state.getBlock().defaultBlockState();
    }

    /** State left after a {@link HarvestMode#KEEP_PLANT} harvest. */
    default BlockState harvestedState(BlockState state) {
        return state;
    }
}
