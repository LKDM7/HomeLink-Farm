package fr.lkdm.homelink.farm.farm.crop;

import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationCoverage;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Incremental, budgeted scan of a zone. A pass starts every {@code cropScanInterval} ticks
 * and examines at most {@code cropScanBudgetPerTick} positions per tick (also capped by the
 * global {@link ScanBudget}), so a large farm is spread over many ticks instead of freezing
 * the server. Unloaded chunks are skipped and reported, never loaded.
 */
public final class CropScanner {
    @Nullable
    private CropZone zone;
    private long cursor = -1;
    private long nextPassAt;
    @Nullable
    private CropScanResult.Builder pass;
    @Nullable
    private CropScanResult lastResult;
    private final BlockPos.MutableBlockPos cursorPos = new BlockPos.MutableBlockPos();

    public void setZone(@Nullable CropZone zone) {
        this.zone = zone;
        cursor = -1;
        pass = null;
        lastResult = null;
        nextPassAt = 0;
    }

    @Nullable
    public CropZone zone() {
        return zone;
    }

    @Nullable
    public CropScanResult lastResult() {
        return lastResult;
    }

    public boolean scanning() {
        return cursor >= 0;
    }

    /** Fraction of the current pass already examined (0 when idle). */
    public float progress() {
        return zone == null || cursor < 0 ? 0 : cursor / (float) zone.volume();
    }

    /** Starts a new pass as soon as possible. */
    public void requestPass() {
        nextPassAt = 0;
    }

    /** Delays the first pass (spreads monitors loaded at the same moment). */
    public void delayFirstPass(long gameTime, int ticks) {
        if (lastResult == null && cursor < 0) nextPassAt = gameTime + ticks;
    }

    /**
     * Advances the scan by at most the allowed budget.
     * @return the completed result when this call finished a pass, otherwise null
     */
    @Nullable
    public CropScanResult tick(ServerLevel level, CropInspector inspector) {
        if (zone == null) return null;
        long now = level.getGameTime();
        if (cursor < 0) {
            if (now < nextPassAt) return null;
            cursor = 0;
            pass = new CropScanResult.Builder();
        }
        int granted = ScanBudget.take(level.getServer(), FarmServerConfig.CROP_SCAN_BUDGET_PER_TICK.get(),
                FarmServerConfig.GLOBAL_SCAN_BUDGET_PER_TICK.get());
        long volume = zone.volume();
        IrrigationCoverage coverage = IrrigationManager.get(level).coverage();
        for (int i = 0; i < granted && cursor < volume; i++, cursor++) {
            BlockPos pos = zone.positionAt(cursor, cursorPos);
            if (!level.isLoaded(pos)) {
                pass.addUnloaded(1);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;
            CropAdapter adapter = CropAdapters.get(state);
            if (adapter != null) inspector.inspect(level, pos, state, adapter, coverage, pass);
        }
        if (cursor < volume) return null;
        lastResult = pass.build(now);
        pass = null;
        cursor = -1;
        nextPassAt = now + FarmServerConfig.CROP_SCAN_INTERVAL.get();
        return lastResult;
    }
}
