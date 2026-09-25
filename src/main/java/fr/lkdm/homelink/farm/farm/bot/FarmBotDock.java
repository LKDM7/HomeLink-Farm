package fr.lkdm.homelink.farm.farm.bot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Rules of the docking spot in front of a FarmBot Station. */
public final class FarmBotDock {
    private FarmBotDock() {
    }

    /** A robot can park here: nothing solid or liquid in the way, and ground to stand on. */
    public static boolean free(Level level, BlockPos dock) {
        if (!level.isLoaded(dock)) return false;
        var state = level.getBlockState(dock);
        if (!state.getCollisionShape(level, dock).isEmpty() || !state.getFluidState().isEmpty()) return false;
        BlockPos below = dock.below();
        return !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
    }
}
