package fr.lkdm.homelink.farm.farm.crop;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Registry of {@link CropAdapter}s. Adapters registered later take precedence, so a
 * third-party mod can override the generic vanilla handling of its own crop block.
 * Lookups are cached per block.
 */
public final class CropAdapters {
    private static final CropAdapter NONE = new CropAdapter() {
        @Override public boolean matches(BlockState state) { return false; }
        @Override public int age(BlockState state) { return 0; }
        @Override public int maxAge(BlockState state) { return 0; }
    };
    private static final List<CropAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final Map<Block, CropAdapter> CACHE = new ConcurrentHashMap<>();

    static {
        VanillaCropAdapters.registerAll();
    }

    private CropAdapters() {
    }

    /** Registers an adapter; call during mod setup. Later registrations win. */
    public static void register(CropAdapter adapter) {
        ADAPTERS.addFirst(Objects.requireNonNull(adapter, "adapter"));
        CACHE.clear();
    }

    /** Returns the adapter handling this state, if the state is a counted crop. */
    public static Optional<CropAdapter> find(BlockState state) {
        return Optional.ofNullable(get(state));
    }

    /** Allocation-free variant of {@link #find} for scan loops; null when not a counted crop. */
    @Nullable
    public static CropAdapter get(BlockState state) {
        CropAdapter adapter = CACHE.computeIfAbsent(state.getBlock(), block -> resolve(state));
        return adapter == NONE || !adapter.isCounted(state) ? null : adapter;
    }

    private static CropAdapter resolve(BlockState state) {
        for (CropAdapter adapter : ADAPTERS) {
            if (adapter.matches(state)) return adapter;
        }
        return NONE;
    }
}
