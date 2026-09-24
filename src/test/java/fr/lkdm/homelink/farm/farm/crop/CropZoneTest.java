package fr.lkdm.homelink.farm.farm.crop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CropZoneTest {
    @Test
    void normalizesCornersAndComputesVolume() {
        CropZone zone = new CropZone(new BlockPos(5, 70, -3), new BlockPos(-2, 68, 4));
        assertEquals(new BlockPos(-2, 68, -3), zone.min());
        assertEquals(new BlockPos(5, 70, 4), zone.max());
        assertEquals(8L * 3 * 8, zone.volume());
    }

    @Test
    void positionAtVisitsEveryBlockExactlyOnce() {
        CropZone zone = new CropZone(new BlockPos(0, 0, 0), new BlockPos(4, 2, 3));
        Set<BlockPos> seen = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (long i = 0; i < zone.volume(); i++) {
            BlockPos pos = zone.positionAt(i, cursor).immutable();
            assertTrue(zone.contains(pos));
            assertTrue(seen.add(pos), "visited twice: " + pos);
        }
        assertEquals(zone.volume(), seen.size());
    }

    @Test
    void validationRejectsLargeAndDistantZones() {
        BlockPos monitor = new BlockPos(0, 64, 0);
        assertEquals(ZoneValidation.Result.OK, ZoneValidation.validate(monitor, CropZone.around(monitor, 4, 1, 1), 32768, 48));
        assertEquals(ZoneValidation.Result.TOO_LARGE, ZoneValidation.validate(monitor, CropZone.around(monitor, 40, 5, 5), 32768, 48));
        assertEquals(ZoneValidation.Result.TOO_FAR,
                ZoneValidation.validate(monitor, new CropZone(new BlockPos(60, 64, 0), new BlockPos(62, 64, 2)), 32768, 48));
    }

    @Test
    void resultBuilderAveragesMaturity() {
        CropScanResult.Builder builder = new CropScanResult.Builder();
        builder.addCrop(1.0F, true);
        builder.addCrop(0.5F, false);
        builder.addCrop(0.0F, false);
        builder.addUnloaded(4);
        CropScanResult result = builder.build(10);
        assertEquals(3, result.crops());
        assertEquals(1, result.ready());
        assertEquals(2, result.growing());
        assertEquals(0.5F, result.maturity(), 1e-6);
        assertEquals(false, result.complete());
    }

    @Test
    void summaryWeightsMaturityByCropCount() {
        FarmSummary.Builder builder = new FarmSummary.Builder();
        builder.addMonitor(new CropScanResult(30, 30, 1.0F, 0, 0));
        builder.addMonitor(new CropScanResult(10, 0, 0.0F, 0, 0));
        FarmSummary summary = builder.build();
        assertEquals(40, summary.crops());
        assertEquals(0.75F, summary.maturity(), 1e-6);
        assertEquals(0.75F, summary.readyFraction(), 1e-6);
    }
}
