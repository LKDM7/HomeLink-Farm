package fr.lkdm.homelink.farm.farm.crop;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.PitcherCropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * Adapters for vanilla crops. {@link CropBlock} is handled generically (wheat, carrots,
 * potatoes, beetroots, torchflowers and most modded crops extending it).
 * Light thresholds mirror the vanilla random-tick code of each block (raw light >= 9).
 */
final class VanillaCropAdapters {
    /** Vanilla crops and stems only grow when raw brightness is at least 9. */
    static final int VANILLA_GROWTH_LIGHT = 9;

    private VanillaCropAdapters() {
    }

    static void registerAll() {
        // Registered from most generic to most specific: later registrations are checked first.
        CropAdapters.register(new CropBlockAdapter());
        CropAdapters.register(new PropertyAdapter(StemBlock.class, StemBlock.AGE, true, VANILLA_GROWTH_LIGHT));
        CropAdapters.register(new AttachedStemAdapter());
        CropAdapters.register(new PitcherAdapter());
        CropAdapters.register(new ReplantedAdapter(NetherWartBlock.class, NetherWartBlock.AGE, false, 0));
        CropAdapters.register(new BerryAdapter());
        CropAdapters.register(new ReplantedAdapter(CocoaBlock.class, CocoaBlock.AGE, false, 0));
    }

    /** Broken when mature and replanted at age 0, keeping its other properties (e.g. cocoa facing). */
    static final class ReplantedAdapter extends PropertyAdapter {
        private final IntegerProperty age;

        ReplantedAdapter(Class<?> blockClass, IntegerProperty age, boolean farmland, int light) {
            super(blockClass, age, farmland, light);
            this.age = age;
        }

        @Override public HarvestMode harvestMode(BlockState state) { return HarvestMode.REPLANT; }
        @Override public BlockState replantState(BlockState state) { return state.setValue(age, 0); }
    }

    /** Any {@link CropBlock}, using its public age API. */
    static final class CropBlockAdapter implements CropAdapter {
        @Override public boolean matches(BlockState state) { return state.getBlock() instanceof CropBlock; }
        @Override public int age(BlockState state) { return ((CropBlock) state.getBlock()).getAge(state); }
        @Override public int maxAge(BlockState state) { return ((CropBlock) state.getBlock()).getMaxAge(); }
        @Override public boolean isMature(BlockState state) { return ((CropBlock) state.getBlock()).isMaxAge(state); }
        @Override public boolean growsOnFarmland(BlockState state) { return true; }
        @Override public boolean acceptsIrrigation(BlockState state) { return true; }
        @Override public int minimumGrowthLight(BlockState state) { return VANILLA_GROWTH_LIGHT; }
        @Override public HarvestMode harvestMode(BlockState state) { return HarvestMode.REPLANT; }
        @Override public BlockState replantState(BlockState state) { return ((CropBlock) state.getBlock()).getStateForAge(0); }
    }

    /** Block whose growth is an integer age property. */
    static class PropertyAdapter implements CropAdapter {
        private final Class<?> blockClass;
        private final IntegerProperty age;
        private final boolean farmland;
        private final int light;

        PropertyAdapter(Class<?> blockClass, IntegerProperty age, boolean farmland, int light) {
            this.blockClass = blockClass;
            this.age = age;
            this.farmland = farmland;
            this.light = light;
        }

        @Override public boolean matches(BlockState state) { return blockClass.isInstance(state.getBlock()) && state.hasProperty(age); }
        @Override public int age(BlockState state) { return state.getValue(age); }
        @Override public int maxAge(BlockState state) { return age.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(0); }
        @Override public boolean growsOnFarmland(BlockState state) { return farmland; }
        @Override public boolean acceptsIrrigation(BlockState state) { return farmland; }
        @Override public int minimumGrowthLight(BlockState state) { return light; }
    }

    /** A stem attached to its fruit has finished growing. */
    static final class AttachedStemAdapter implements CropAdapter {
        @Override public boolean matches(BlockState state) { return state.getBlock() instanceof AttachedStemBlock; }
        @Override public int age(BlockState state) { return 1; }
        @Override public int maxAge(BlockState state) { return 1; }
        @Override public boolean growsOnFarmland(BlockState state) { return true; }
    }

    /** Pitcher crop: two blocks tall, only the lower half is counted. */
    static final class PitcherAdapter extends PropertyAdapter {
        PitcherAdapter() {
            super(PitcherCropBlock.class, PitcherCropBlock.AGE, true, 0);
        }

        @Override
        public boolean isCounted(BlockState state) {
            return state.getValue(PitcherCropBlock.HALF) == DoubleBlockHalf.LOWER;
        }
    }

    /** Sweet berry bush: samples light above itself, like its vanilla random tick. */
    static final class BerryAdapter extends PropertyAdapter {
        BerryAdapter() {
            super(SweetBerryBushBlock.class, SweetBerryBushBlock.AGE, false, VANILLA_GROWTH_LIGHT);
        }

        @Override
        public BlockPos lightSamplePos(BlockPos pos, BlockState state) {
            return pos.above();
        }

        /** Picked like a player does: the bush stays and goes back to its fruitless stage. */
        @Override public HarvestMode harvestMode(BlockState state) { return HarvestMode.KEEP_PLANT; }
        @Override public BlockState harvestedState(BlockState state) { return state.setValue(SweetBerryBushBlock.AGE, 1); }

        /** Same amount as a player picking a ripe bush (the bush's loot table only covers breaking it). */
        @Override
        public List<ItemStack> harvestDrops(ServerLevel level, BlockPos pos, BlockState state, @Nullable Entity harvester) {
            int count = 1 + level.random.nextInt(2) + (state.getValue(SweetBerryBushBlock.AGE) == 3 ? 1 : 0);
            return List.of(new ItemStack(Items.SWEET_BERRIES, count));
        }
    }
}
