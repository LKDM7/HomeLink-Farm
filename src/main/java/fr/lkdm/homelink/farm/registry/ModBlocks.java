package fr.lkdm.homelink.farm.registry;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.block.CropMonitorBlock;
import fr.lkdm.homelink.farm.block.FarmBotStationBlock;
import fr.lkdm.homelink.farm.block.FarmControllerBlock;
import fr.lkdm.homelink.farm.block.IrrigationPumpBlock;
import fr.lkdm.homelink.farm.block.pipe.CopperPipeBlock;
import fr.lkdm.homelink.farm.block.pipe.WeatheringCopperPipeBlock;
import java.util.List;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WeatheringCopper.WeatherState;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(HomeLinkFarm.MOD_ID);

    public static final DeferredBlock<FarmControllerBlock> FARM_CONTROLLER = BLOCKS.register("farm_controller",
            () -> new FarmControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<CropMonitorBlock> CROP_MONITOR = BLOCKS.register("crop_monitor",
            () -> new CropMonitorBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(2.5F, 6.0F).sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredBlock<IrrigationPumpBlock> IRRIGATION_PUMP = BLOCKS.register("irrigation_pump",
            () -> new IrrigationPumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(3.0F, 6.0F).sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredBlock<CopperSprinklerBlock> COPPER_SPRINKLER = BLOCKS.register("copper_sprinkler",
            () -> new CopperSprinklerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F).sound(SoundType.COPPER).noOcclusion()));

    public static final DeferredBlock<FarmBotStationBlock> FARMBOT_STATION = BLOCKS.register("farmbot_station",
            () -> new FarmBotStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL).strength(3.0F, 6.0F).sound(SoundType.METAL).noOcclusion()));

    public static final DeferredBlock<WeatheringCopperPipeBlock> COPPER_PIPE =weatheringPipe("copper_pipe", WeatherState.UNAFFECTED);
    public static final DeferredBlock<WeatheringCopperPipeBlock> EXPOSED_COPPER_PIPE = weatheringPipe("exposed_copper_pipe", WeatherState.EXPOSED);
    public static final DeferredBlock<WeatheringCopperPipeBlock> WEATHERED_COPPER_PIPE = weatheringPipe("weathered_copper_pipe", WeatherState.WEATHERED);
    public static final DeferredBlock<WeatheringCopperPipeBlock> OXIDIZED_COPPER_PIPE = weatheringPipe("oxidized_copper_pipe", WeatherState.OXIDIZED);
    public static final DeferredBlock<CopperPipeBlock> WAXED_COPPER_PIPE = waxedPipe("waxed_copper_pipe", WeatherState.UNAFFECTED);
    public static final DeferredBlock<CopperPipeBlock> WAXED_EXPOSED_COPPER_PIPE = waxedPipe("waxed_exposed_copper_pipe", WeatherState.EXPOSED);
    public static final DeferredBlock<CopperPipeBlock> WAXED_WEATHERED_COPPER_PIPE = waxedPipe("waxed_weathered_copper_pipe", WeatherState.WEATHERED);
    public static final DeferredBlock<CopperPipeBlock> WAXED_OXIDIZED_COPPER_PIPE = waxedPipe("waxed_oxidized_copper_pipe", WeatherState.OXIDIZED);

    /** Every pipe variant, in creative-tab order. */
    public static final List<DeferredBlock<? extends CopperPipeBlock>> PIPES = List.of(
            COPPER_PIPE, EXPOSED_COPPER_PIPE, WEATHERED_COPPER_PIPE, OXIDIZED_COPPER_PIPE,
            WAXED_COPPER_PIPE, WAXED_EXPOSED_COPPER_PIPE, WAXED_WEATHERED_COPPER_PIPE, WAXED_OXIDIZED_COPPER_PIPE);

    private static BlockBehaviour.Properties pipeProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(2.0F, 6.0F).sound(SoundType.COPPER).noOcclusion();
    }

    private static DeferredBlock<WeatheringCopperPipeBlock> weatheringPipe(String name, WeatherState state) {
        return BLOCKS.register(name, () -> new WeatheringCopperPipeBlock(state, pipeProperties().randomTicks()));
    }

    private static DeferredBlock<CopperPipeBlock> waxedPipe(String name, WeatherState state) {
        return BLOCKS.register(name, () -> new CopperPipeBlock(state, pipeProperties()));
    }

    private ModBlocks() {
    }
}
