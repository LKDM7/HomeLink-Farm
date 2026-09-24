package fr.lkdm.homelink.farm.farm.irrigation;

import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.crop.CropAdapter;
import fr.lkdm.homelink.farm.farm.crop.CropAdapters;
import it.unimi.dsi.fastutil.longs.LongIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Applies the irrigation growth bonus ({@link GrowthBonus}) once every {@link #INTERVAL}
 * ticks per level. Only positions of the coverage UNION are visited (never stacked), the
 * random draw happens before any world access, and only compatible crops in block-ticking
 * chunks (where vanilla random ticks happen too) receive the extra vanilla random tick.
 */
public final class IrrigationGrowth {
    public static final int INTERVAL = 20;

    private IrrigationGrowth() {
    }

    public static void tick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL != 0) return;
        IrrigationCoverage coverage = IrrigationManager.get(level).coverage();
        if (coverage.irrigatedSize() == 0) return;
        double expected = GrowthBonus.expectedExtraTicks(FarmServerConfig.IRRIGATION_GROWTH_BONUS.get(),
                level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING), INTERVAL);
        apply(level, coverage, expected);
    }

    /**
     * Gives each irrigated position its sampled number of extra growth ticks.
     * @return number of extra growth ticks actually delivered to crops
     */
    public static int apply(ServerLevel level, IrrigationCoverage coverage, double expected) {
        if (expected <= 0) return 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int delivered = 0;
        for (LongIterator iterator = coverage.irrigatedPositions().iterator(); iterator.hasNext(); ) {
            long packed = iterator.nextLong();
            int ticks = GrowthBonus.sample(expected, level.random);
            if (ticks == 0) continue;
            cursor.set(packed);
            if (!level.isLoaded(cursor) || !level.shouldTickBlocksAt(ChunkPos.asLong(cursor))) continue;
            for (int i = 0; i < ticks; i++) {
                BlockState state = level.getBlockState(cursor);
                CropAdapter adapter = CropAdapters.get(state);
                if (adapter == null || !adapter.acceptsIrrigation(state)) break;
                adapter.applyGrowthTick(state, level, cursor.immutable(), level.random);
                delivered++;
            }
        }
        return delivered;
    }
}
