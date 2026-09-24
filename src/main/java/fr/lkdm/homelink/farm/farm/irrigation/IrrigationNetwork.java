package fr.lkdm.homelink.farm.farm.irrigation;

import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * A connected set of pipes, pumps and sprinklers, computed once by {@link IrrigationManager}
 * and reused until a structural change invalidates it. Its hydraulic state is re-evaluated
 * at most once per tick from the (cheap) state of its pumps.
 */
public final class IrrigationNetwork {
    private final Set<BlockPos> nodes;
    private final List<BlockPos> pumps;
    private final List<BlockPos> sprinklers;
    private final int pipes;
    private final boolean incomplete;
    private final boolean tooLarge;
    private final long builtAt;
    private boolean valid = true;
    private long evaluatedAt = Long.MIN_VALUE;
    private NetworkState state = NetworkState.INACTIVE;
    private int supplyingPumps;

    IrrigationNetwork(Set<BlockPos> nodes, List<BlockPos> pumps, List<BlockPos> sprinklers, int pipes,
                      boolean incomplete, boolean tooLarge, long builtAt) {
        this.nodes = Set.copyOf(nodes);
        this.pumps = List.copyOf(pumps);
        this.sprinklers = List.copyOf(sprinklers);
        this.pipes = pipes;
        this.incomplete = incomplete;
        this.tooLarge = tooLarge;
        this.builtAt = builtAt;
    }

    /** Recomputes the state if not already done this tick. */
    public NetworkState evaluate(ServerLevel level) {
        long now = level.getGameTime();
        if (evaluatedAt == now) return state;
        evaluatedAt = now;
        int supplying = 0;
        for (BlockPos pump : pumps) {
            if (level.isLoaded(pump) && level.getBlockEntity(pump) instanceof IrrigationPumpBlockEntity entity && entity.canSupply()) {
                supplying++;
            }
        }
        supplyingPumps = supplying;
        state = IrrigationRules.evaluate(supplying, sprinklers.size(), FarmServerConfig.MAX_SPRINKLERS_PER_PUMP.get(), tooLarge);
        return state;
    }

    public NetworkState state() { return state; }
    public int supplyingPumps() { return supplyingPumps; }
    public int capacity() { return IrrigationRules.capacity(supplyingPumps, FarmServerConfig.MAX_SPRINKLERS_PER_PUMP.get()); }
    public Set<BlockPos> nodes() { return nodes; }
    public List<BlockPos> pumps() { return pumps; }
    public List<BlockPos> sprinklers() { return sprinklers; }
    public int pipes() { return pipes; }
    /** Part of the network touches unloaded chunks: only the loaded part is known. */
    public boolean incomplete() { return incomplete; }
    public long builtAt() { return builtAt; }
    public boolean valid() { return valid; }

    void invalidate() {
        valid = false;
    }
}
