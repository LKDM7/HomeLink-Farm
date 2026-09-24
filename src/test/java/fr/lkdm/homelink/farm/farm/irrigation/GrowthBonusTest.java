package fr.lkdm.homelink.farm.farm.irrigation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

class GrowthBonusTest {
    @Test
    void extraTickRateIsExactlyTwentyPercentOfVanilla() {
        int randomTickSpeed = 3;
        int interval = 20;
        double extraPerApplication = GrowthBonus.expectedExtraTicks(0.20, randomTickSpeed, interval);
        double extraPerTick = extraPerApplication / interval;
        double vanillaPerTick = randomTickSpeed / 4096.0;
        assertEquals(0.20, extraPerTick / vanillaPerTick, 1e-12);
        assertEquals(0.0029296875, extraPerApplication, 1e-12);
    }

    @Test
    void scalesWithTheGameRuleWithoutChangingIt() {
        assertEquals(0.20, GrowthBonus.expectedExtraTicks(0.20, 30, 1) / (30 / 4096.0), 1e-12);
        assertEquals(0, GrowthBonus.expectedExtraTicks(0.20, 0, 20));
        assertEquals(0, GrowthBonus.expectedExtraTicks(0.0, 3, 20));
    }

    @Test
    void samplingMatchesTheExpectation() {
        RandomSource random = RandomSource.create(42);
        double expected = GrowthBonus.expectedExtraTicks(0.20, 3, 20);
        int draws = 2_000_000;
        long total = 0;
        for (int i = 0; i < draws; i++) total += GrowthBonus.sample(expected, random);
        double mean = total / (double) draws;
        assertTrue(Math.abs(mean - expected) / expected < 0.03, "mean " + mean + " vs " + expected);
    }

    @Test
    void largeExpectationsKeepTheirWholePart() {
        RandomSource random = RandomSource.create(1);
        for (int i = 0; i < 1000; i++) {
            int ticks = GrowthBonus.sample(2.25, random);
            assertTrue(ticks == 2 || ticks == 3);
        }
    }
}
