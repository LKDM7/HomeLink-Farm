package fr.lkdm.homelink.farm.farm.irrigation;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Implemented by blocks that are part of a copper irrigation network. */
public interface IrrigationConnectable {
    enum NodeKind { PIPE, PUMP, SPRINKLER }

    NodeKind nodeKind();

    /** Whether water can flow through the given face of this block. */
    default boolean connectsIrrigation(BlockState state, Direction face) {
        return true;
    }

    static boolean connects(BlockState state, Direction face) {
        return state.getBlock() instanceof IrrigationConnectable node && node.connectsIrrigation(state, face);
    }
}
