package fr.lkdm.homelink.farm.farm.irrigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class CoverageAreaTest {
    private static final BlockPos SPRINKLER = new BlockPos(0, 64, 0);

    @Test
    void standingSprinklerCoversTwoBelowToOneAbove() {
        LongOpenHashSet area = new LongOpenHashSet();
        IrrigationCoverage.addArea(area, SPRINKLER, 2, false);
        assertEquals(5 * 5 * 4, area.size());
        assertTrue(area.contains(BlockPos.asLong(2, 62, -2)));
        assertTrue(area.contains(BlockPos.asLong(0, 65, 0)));
        assertFalse(area.contains(BlockPos.asLong(0, 61, 0)));
        assertFalse(area.contains(BlockPos.asLong(3, 64, 0)));
    }

    @Test
    void hangingSprinklerCoversThreeBelowToItsLevel() {
        LongOpenHashSet area = new LongOpenHashSet();
        IrrigationCoverage.addArea(area, SPRINKLER, 2, true);
        assertEquals(5 * 5 * 4, area.size());
        assertTrue(area.contains(BlockPos.asLong(0, 61, 0)));
        assertTrue(area.contains(BlockPos.asLong(-2, 64, 2)));
        assertFalse(area.contains(BlockPos.asLong(0, 65, 0)));
        assertFalse(area.contains(BlockPos.asLong(0, 60, 0)));
    }

    @Test
    void overlappingAreasAreCountedOnce() {
        LongOpenHashSet union = new LongOpenHashSet();
        IrrigationCoverage.addArea(union, SPRINKLER, 2, false);
        IrrigationCoverage.addArea(union, SPRINKLER.east(2), 2, false);
        assertEquals(7 * 5 * 4, union.size());
    }
}
