package fr.lkdm.homelink.farm.farm.irrigation;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/**
 * Positions covered by sprinklers, as a UNION: a crop under two or three sprinklers appears
 * once, which is what prevents the growth bonus from stacking.
 * <p>Area of one sprinkler: a square of {@code 2 * range + 1} blocks centered on it (5 x 5 with
 * the default range 2). Vertically, a standing sprinkler covers from 12 blocks below to 1 block
 * above itself (crops at its level, or one level below when raised on a pipe); a hanging
 * sprinkler (under a pipe) covers from 12 blocks below up to its own level, so overhead pipes
 * can water the crops underneath.</p>
 */
public final class IrrigationCoverage {
    public static final int BELOW = 12;
    public static final int ABOVE = 1;
    public static final int HANGING_BELOW = 12;
    public static final int HANGING_ABOVE = 0;
    public static final IrrigationCoverage EMPTY = new IrrigationCoverage(new LongOpenHashSet(), new LongOpenHashSet(), 0, 0);

    private final LongSet irrigated;
    private final LongSet offline;
    private final long builtAt;
    private final long structureVersion;

    IrrigationCoverage(LongSet irrigated, LongSet offline, long builtAt, long structureVersion) {
        this.irrigated = irrigated;
        this.offline = offline;
        this.builtAt = builtAt;
        this.structureVersion = structureVersion;
    }

    /** A coverage made of the given irrigated positions only (tools and verification). */
    public static IrrigationCoverage ofIrrigated(LongSet irrigatedPositions) {
        return new IrrigationCoverage(new LongOpenHashSet(irrigatedPositions), new LongOpenHashSet(), 0, 0);
    }

    /** Covered by at least one sprinkler of an ACTIVE network. */
    public boolean irrigated(BlockPos pos) {
        return irrigated.contains(pos.asLong());
    }

    /** Covered only by sprinklers that do not work (disconnected, dry, disabled or overloaded network). */
    public boolean offline(BlockPos pos) {
        return !irrigated(pos) && offline.contains(pos.asLong());
    }

    public LongSet irrigatedPositions() {
        return irrigated;
    }

    public int irrigatedSize() {
        return irrigated.size();
    }

    long builtAt() {
        return builtAt;
    }

    long structureVersion() {
        return structureVersion;
    }

    /** Area covered by a sprinkler at {@code sprinkler} with the given horizontal range. */
    public static AABB area(BlockPos sprinkler, int range, boolean hanging) {
        int below = hanging ? HANGING_BELOW : BELOW;
        int above = hanging ? HANGING_ABOVE : ABOVE;
        return new AABB(sprinkler.getX() - range, sprinkler.getY() - below, sprinkler.getZ() - range,
                sprinkler.getX() + range + 1, sprinkler.getY() + above + 1, sprinkler.getZ() + range + 1);
    }

    static void addArea(LongSet target, BlockPos sprinkler, int range, boolean hanging) {
        int below = hanging ? HANGING_BELOW : BELOW;
        int above = hanging ? HANGING_ABOVE : ABOVE;
        for (int y = -below; y <= above; y++) {
            for (int x = -range; x <= range; x++) {
                for (int z = -range; z <= range; z++) {
                    target.add(BlockPos.asLong(sprinkler.getX() + x, sprinkler.getY() + y, sprinkler.getZ() + z));
                }
            }
        }
    }
}
