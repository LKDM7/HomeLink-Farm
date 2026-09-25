package fr.lkdm.homelink.farm.farm.crop;

import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationCoverage;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Examines one crop found during a scan: maturity, irrigation state (IRRIGATED,
 * NOT_IRRIGATED, IRRIGATION_OFFLINE) and reliable diagnostics. Mature crops are not
 * diagnosed (they do not need to grow any more) but still count for coverage.
 */
public final class CropInspector {
    public static final CropInspector INSTANCE = new CropInspector();

    private CropInspector() {
    }

    public void inspect(ServerLevel level, BlockPos pos, BlockState state, CropAdapter adapter, IrrigationCoverage coverage,
                        CropScanResult.Builder pass) {
        boolean mature = adapter.isMature(state);
        pass.addCrop(adapter.maturity(state), mature);
        if (mature && adapter.harvestMode(state) != CropAdapter.HarvestMode.NONE) pass.addHarvestable(pos);
        if (adapter.acceptsIrrigation(state)) {
            if (coverage.irrigated(pos)) {
                pass.addIrrigated();
            } else if (coverage.offline(pos)) {
                pass.addIrrigationOffline(pos, mature);
            } else {
                pass.addNotIrrigated(pos, mature);
            }
        }
        if (mature) return;
        if (adapter.growsOnFarmland(state)) {
            BlockState soil = level.getBlockState(pos.below());
            if (soil.getBlock() instanceof FarmBlock && soil.getValue(FarmBlock.MOISTURE) == 0) {
                pass.addProblem(pos, ProblemType.DRY_FARMLAND);
            }
        }
        int minimumLight = adapter.minimumGrowthLight(state);
        if (minimumLight > 0 && level.getRawBrightness(adapter.lightSamplePos(pos, state), 0) < minimumLight) {
            pass.addProblem(pos, ProblemType.LOW_LIGHT);
        }
    }
}
