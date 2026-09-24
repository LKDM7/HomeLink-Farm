package fr.lkdm.homelink.farm.farm.irrigation;

import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Per-level registry of irrigation networks. Networks are discovered by a breadth-first walk
 * from a pump, cached, and rebuilt only after a structural change next to one of their nodes
 * (pipe/pump/sprinkler placed or removed, pump/sprinkler chunk unloaded) or after
 * {@link #REBUILD_AFTER_TICKS} as a self-healing safety net. The walk never loads chunks:
 * reaching an unloaded chunk marks the network incomplete. Server thread only.
 */
public final class IrrigationManager {
    /** Periodic rebuild (60 s) that heals any missed invalidation. */
    public static final int REBUILD_AFTER_TICKS = 1200;
    /** Coverage follows pump/network state changes with at most this delay. */
    public static final int COVERAGE_REFRESH_TICKS = 20;

    private static final Map<ServerLevel, IrrigationManager> MANAGERS = new WeakHashMap<>();

    private final ServerLevel level;
    private final Map<BlockPos, IrrigationNetwork> index = new HashMap<>();
    private final Set<BlockPos> sprinklers = new LinkedHashSet<>();
    private long structureVersion;
    private IrrigationCoverage coverage = IrrigationCoverage.EMPTY;

    private IrrigationManager(ServerLevel level) {
        this.level = level;
    }

    public static IrrigationManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, IrrigationManager::new);
    }

    public static void forget(ServerLevel level) {
        MANAGERS.remove(level);
    }

    /** Incremented on every structural change; lets caches (coverage) know when to rebuild. */
    public long structureVersion() {
        return structureVersion;
    }

    public Set<BlockPos> loadedSprinklers() {
        return Collections.unmodifiableSet(sprinklers);
    }

    public void sprinklerLoaded(BlockPos pos) {
        sprinklers.add(pos.immutable());
        invalidateAround(pos);
    }

    public void sprinklerUnloaded(BlockPos pos) {
        sprinklers.remove(pos);
        invalidateAround(pos);
    }

    /** Called when an irrigation block appears, disappears or its chunk (un)loads. */
    public void invalidateAround(BlockPos pos) {
        structureVersion++;
        dispose(index.get(pos));
        for (Direction direction : Direction.values()) dispose(index.get(pos.relative(direction)));
    }

    private void dispose(IrrigationNetwork network) {
        if (network == null || !network.valid()) return;
        network.invalidate();
        for (BlockPos node : network.nodes()) index.remove(node, network);
    }

    /**
     * Union of the areas of loaded sprinklers, split into irrigated and offline positions.
     * Rebuilt at most once per {@link #COVERAGE_REFRESH_TICKS}, or right after a structural change.
     */
    public IrrigationCoverage coverage() {
        long now = level.getGameTime();
        if (coverage.structureVersion() == structureVersion && now - coverage.builtAt() < COVERAGE_REFRESH_TICKS && coverage != IrrigationCoverage.EMPTY) {
            return coverage;
        }
        int range = FarmServerConfig.SPRINKLER_RANGE.get();
        LongOpenHashSet irrigated = new LongOpenHashSet();
        LongOpenHashSet offline = new LongOpenHashSet();
        for (BlockPos sprinkler : sprinklers) {
            boolean active = networkAt(sprinkler).map(network -> network.evaluate(level).irrigates()).orElse(false);
            IrrigationCoverage.addArea(active ? irrigated : offline, sprinkler, range, hanging(sprinkler));
        }
        coverage = new IrrigationCoverage(irrigated, offline, now, structureVersion);
        return coverage;
    }

    /**
     * Irrigable crops covered by the sprinklers of an ACTIVE network (union, loaded positions
     * only). Reads at most {@code sprinklers x area} block states; call periodically, not per tick.
     */
    public int countIrrigatedCrops(IrrigationNetwork network) {
        if (!network.evaluate(level).irrigates()) return 0;
        LongOpenHashSet area = new LongOpenHashSet();
        int range = FarmServerConfig.SPRINKLER_RANGE.get();
        for (BlockPos sprinkler : network.sprinklers()) IrrigationCoverage.addArea(area, sprinkler, range, hanging(sprinkler));
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int crops = 0;
        for (var iterator = area.iterator(); iterator.hasNext(); ) {
            cursor.set(iterator.nextLong());
            if (!level.isLoaded(cursor)) continue;
            BlockState state = level.getBlockState(cursor);
            var adapter = fr.lkdm.homelink.farm.farm.crop.CropAdapters.get(state);
            if (adapter != null && adapter.acceptsIrrigation(state)) crops++;
        }
        return crops;
    }

    private boolean hanging(BlockPos sprinkler) {
        return level.isLoaded(sprinkler) && CopperSprinklerBlock.isHanging(level.getBlockState(sprinkler));
    }

    /** The current network containing this node, if one has been built. */
    public Optional<IrrigationNetwork> networkAt(BlockPos pos) {
        IrrigationNetwork network = index.get(pos);
        return network != null && network.valid() ? Optional.of(network) : Optional.empty();
    }

    /** The network fed by this pump, (re)built if needed. */
    public IrrigationNetwork networkForPump(BlockPos pump) {
        IrrigationNetwork network = index.get(pump);
        if (network != null && network.valid() && level.getGameTime() - network.builtAt() < REBUILD_AFTER_TICKS) return network;
        dispose(network);
        return build(pump);
    }

    private IrrigationNetwork build(BlockPos start) {
        int maxNodes = FarmServerConfig.MAX_NETWORK_NODES.get();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> pumps = new ArrayList<>();
        List<BlockPos> sprinklerNodes = new ArrayList<>();
        int pipes = 0;
        boolean incomplete = false;
        boolean tooLarge = false;
        BlockPos origin = start.immutable();
        visited.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof IrrigationConnectable node)) continue;
            switch (node.nodeKind()) {
                case PIPE -> pipes++;
                case PUMP -> pumps.add(pos);
                case SPRINKLER -> sprinklerNodes.add(pos);
            }
            // Sprinklers are end points: water does not flow through them to other nodes.
            if (node.nodeKind() == IrrigationConnectable.NodeKind.SPRINKLER) continue;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (visited.contains(next) || !node.connectsIrrigation(state, direction)) continue;
                if (!level.isLoaded(next)) {
                    incomplete = true;
                    continue;
                }
                if (!IrrigationConnectable.connects(level.getBlockState(next), direction.getOpposite())) continue;
                if (visited.size() >= maxNodes) {
                    tooLarge = true;
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
        IrrigationNetwork network = new IrrigationNetwork(visited, pumps, sprinklerNodes, pipes, incomplete, tooLarge, level.getGameTime());
        for (BlockPos node : visited) {
            IrrigationNetwork previous = index.get(node);
            if (previous != null && previous != network) dispose(previous);
            index.put(node, network);
        }
        return network;
    }
}
