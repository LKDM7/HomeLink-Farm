package fr.lkdm.homelink.farm.farm.bot;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Answers "which mature crop next?" from the Crop Monitor's last scan, never by scanning the
 * world: candidates come from {@link CropScanResult#harvestable()}, are filtered (zone, loaded
 * chunk, exclusions) and the nearest one that is still mature on the server wins.
 */
public final class FarmBotTargets {
    private FarmBotTargets() {
    }

    @Nullable
    public static BlockPos nearest(ServerLevel level, CropMonitorBlockEntity monitor, Vec3 from, Predicate<BlockPos> excluded) {
        CropScanResult result = monitor.result().orElse(null);
        CropZone zone = monitor.zone().orElse(null);
        if (result == null || zone == null) return null;
        // One linear pass (at most MAX_HARVESTABLE positions): crops already picked since the scan are skipped.
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : result.harvestable()) {
            double distance = pos.distToCenterSqr(from);
            if (distance >= bestDistance || !zone.contains(pos) || excluded.test(pos)) continue;
            if (!CropHarvester.harvestable(level, pos)) continue;
            best = pos;
            bestDistance = distance;
        }
        return best;
    }
}
