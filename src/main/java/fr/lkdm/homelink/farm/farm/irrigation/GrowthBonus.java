package fr.lkdm.homelink.farm.farm.irrigation;

import fr.lkdm.homelink.farm.config.FarmServerConfig;
import net.minecraft.util.RandomSource;

/**
 * The +33% growth speed, derived from how vanilla grows crops.
 * <p>Vanilla picks {@code randomTickSpeed} random blocks per 16x16x16 section (4096 blocks)
 * every tick, so each block receives on average {@code randomTickSpeed / 4096} random ticks
 * per tick, and a crop's growth chance per random tick is fixed. Giving an irrigated crop
 * {@code bonus x randomTickSpeed / 4096} EXTRA random ticks per tick therefore makes it grow
 * {@code (1 + bonus)} times faster on average: 1.33x with the default bonus of 0.33.</p>
 * <p>The extra ticks are applied every {@code interval} ticks, so the expected number of
 * extra ticks per crop per application is {@code bonus x randomTickSpeed x interval / 4096}
 * (0.00293 with the defaults). The {@code randomTickSpeed} game rule is only read, never changed.</p>
 */
public final class GrowthBonus {
    public static final int VANILLA_SECTION_BLOCKS = 4096;

    private GrowthBonus() {
    }

    /** Official default: +33% growth speed. */
    public static final double DEFAULT_BONUS = 0.33;

    /** The configured bonus (server config, synchronized to clients), or the default before it is loaded. */
    public static double configuredBonus() {
        try {
            return FarmServerConfig.IRRIGATION_GROWTH_BONUS.get();
        } catch (IllegalStateException notLoaded) {
            return DEFAULT_BONUS;
        }
    }

    /** Expected extra random ticks per irrigated crop for one application. */
    public static double expectedExtraTicks(double bonus, int randomTickSpeed, int interval) {
        if (bonus <= 0 || randomTickSpeed <= 0) return 0;
        return bonus * randomTickSpeed * interval / VANILLA_SECTION_BLOCKS;
    }

    /** Samples how many extra ticks one crop receives: floor(expected) plus one more with the fractional probability. */
    public static int sample(double expected, RandomSource random) {
        int whole = (int) expected;
        double fraction = expected - whole;
        return whole + (random.nextDouble() < fraction ? 1 : 0);
    }
}
