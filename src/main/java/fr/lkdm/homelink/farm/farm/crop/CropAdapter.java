package fr.lkdm.homelink.farm.farm.crop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

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
}
