package fr.lkdm.homelink.farm.block.pipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Unwaxed pipe that oxidizes on its own. Unlike vanilla copper, whose aging nearly stops when
 * other copper is nearby (as in any pipe line), every pipe ages at the same pace: a new pipe is
 * fully oxidized after about {@code pipeOxidationDays} in-game days (100 by default) spent in a
 * loaded chunk. Each of the three stages is split into {@link #STEPS} hidden steps, so the time
 * varies by about 15% instead of being pure luck. Scraping with an axe and waxing with honeycomb
 * are driven by NeoForge's {@code neoforge:oxidizables} and {@code neoforge:waxables} data maps.
 */
public class WeatheringCopperPipeBlock extends CopperPipeBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperPipeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperPipeBlock::getAge),
            propertiesCodec()).apply(instance, WeatheringCopperPipeBlock::new));
    /** Hidden steps per oxidation stage (not rendered). */
    public static final int STEPS = 8;
    /** Oxidation stages from new copper to fully oxidized. */
    public static final int STAGES = 3;
    public static final IntegerProperty OXIDATION = IntegerProperty.create("oxidation", 0, STEPS - 1);
    /** A block receives on average randomTickSpeed random ticks per 4096 ticks (one chunk section). */
    private static final double SECTION_BLOCKS = 16 * 16 * 16;

    public WeatheringCopperPipeBlock(WeatheringCopper.WeatherState weatherState, Properties properties) {
        super(weatherState, properties);
    }

    @Override
    protected MapCodec<? extends CopperPipeBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OXIDATION);
    }

    @Override
    public WeatheringCopper.WeatherState getAge() {
        return weatherState();
    }

    /**
     * Chance for one random tick to advance one hidden step, so that the {@code STAGES * STEPS}
     * steps take {@code days} in-game days on average at the given random tick speed.
     */
    public static double stepChance(int days, int randomTickSpeed) {
        double randomTicks = days * 24000.0 * randomTickSpeed / SECTION_BLOCKS;
        return Math.min(1.0, STAGES * STEPS / randomTicks);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int speed = Math.max(1, level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING));
        if (random.nextDouble() >= stepChance(FarmServerConfig.PIPE_OXIDATION_DAYS.get(), speed)) return;
        int step = state.getValue(OXIDATION);
        if (step + 1 < STEPS) {
            // Hidden progress: no neighbor update; clients just receive the new state.
            level.setBlock(pos, state.setValue(OXIDATION, step + 1), Block.UPDATE_CLIENTS);
            return;
        }
        WeatheringCopper.getNext(state.getBlock()).ifPresent(next -> {
            BlockState aged = next.withPropertiesOf(state);
            if (aged.hasProperty(OXIDATION)) aged = aged.setValue(OXIDATION, 0);
            level.setBlockAndUpdate(pos, aged);
        });
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }
}
