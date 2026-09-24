package fr.lkdm.homelink.farm.block.pipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Unwaxed pipe that oxidizes over time exactly like vanilla copper blocks. The next stage,
 * scraping with an axe and waxing with honeycomb are driven by NeoForge's
 * {@code neoforge:oxidizables} and {@code neoforge:waxables} data maps shipped by this mod.
 */
public class WeatheringCopperPipeBlock extends CopperPipeBlock implements WeatheringCopper {
    public static final MapCodec<WeatheringCopperPipeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(WeatheringCopperPipeBlock::getAge),
            propertiesCodec()).apply(instance, WeatheringCopperPipeBlock::new));

    public WeatheringCopperPipeBlock(WeatheringCopper.WeatherState weatherState, Properties properties) {
        super(weatherState, properties);
    }

    @Override
    protected MapCodec<? extends CopperPipeBlock> codec() {
        return CODEC;
    }

    @Override
    public WeatheringCopper.WeatherState getAge() {
        return weatherState();
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        changeOverTime(state, level, pos, random);
    }

    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return WeatheringCopper.getNext(state.getBlock()).isPresent();
    }
}
