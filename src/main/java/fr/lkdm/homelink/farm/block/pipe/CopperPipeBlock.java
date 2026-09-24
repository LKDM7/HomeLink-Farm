package fr.lkdm.homelink.farm.block.pipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lkdm.homelink.farm.block.FarmTooltips;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationConnectable;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.WeatheringCopper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Copper Irrigation Pipe. Connects automatically on all six faces to other pipes, pumps and
 * sprinklers. This class is the waxed (non-weathering) variant; {@link WeatheringCopperPipeBlock}
 * oxidizes like vanilla copper. Oxidation is purely cosmetic: every variant carries water
 * identically. Pipes can be laid underwater (waterlogged), which also keeps flowing water
 * around a submerged pump from washing them away.
 */
public class CopperPipeBlock extends Block implements IrrigationConnectable, SimpleWaterloggedBlock {
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final MapCodec<CopperPipeBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            WeatheringCopper.WeatherState.CODEC.fieldOf("weathering_state").forGetter(CopperPipeBlock::weatherState),
            propertiesCodec()).apply(instance, CopperPipeBlock::new));

    public static final Map<Direction, BooleanProperty> CONNECTIONS = PipeBlock.PROPERTY_BY_DIRECTION;
    private static final VoxelShape CORE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> ARMS = Map.of(
            Direction.NORTH, Shapes.or(Block.box(6, 6, 0, 10, 10, 6), Block.box(5.75, 5.75, 0, 10.25, 10.25, .5)),
            Direction.SOUTH, Shapes.or(Block.box(6, 6, 10, 10, 10, 16), Block.box(5.75, 5.75, 15.5, 10.25, 10.25, 16)),
            Direction.WEST, Shapes.or(Block.box(0, 6, 6, 6, 10, 10), Block.box(0, 5.75, 5.75, .5, 10.25, 10.25)),
            Direction.EAST, Shapes.or(Block.box(10, 6, 6, 16, 10, 10), Block.box(15.5, 5.75, 5.75, 16, 10.25, 10.25)),
            Direction.DOWN, Shapes.or(Block.box(6, 0, 6, 10, 6, 10), Block.box(5.75, 0, 5.75, 10.25, .5, 10.25)),
            Direction.UP, Shapes.or(Block.box(6, 10, 6, 10, 16, 10), Block.box(5.75, 15.5, 5.75, 10.25, 16, 10.25)));
    private static final VoxelShape[] SHAPES = new VoxelShape[64];

    static {
        for (int mask = 0; mask < 64; mask++) {
            VoxelShape shape = CORE;
            for (Direction direction : Direction.values()) {
                if ((mask & (1 << direction.ordinal())) != 0) shape = Shapes.or(shape, ARMS.get(direction));
            }
            SHAPES[mask] = shape.optimize();
        }
    }

    private final WeatheringCopper.WeatherState weatherState;

    public CopperPipeBlock(WeatheringCopper.WeatherState weatherState, Properties properties) {
        super(properties);
        this.weatherState = weatherState;
        BlockState state = stateDefinition.any();
        for (BooleanProperty property : CONNECTIONS.values()) state = state.setValue(property, false);
        registerDefaultState(state.setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends CopperPipeBlock> codec() {
        return CODEC;
    }

    public WeatheringCopper.WeatherState weatherState() {
        return weatherState;
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.PIPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        BlockState state = defaultBlockState().setValue(WATERLOGGED, water);
        for (Direction direction : Direction.values()) {
            BlockState neighbor = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            state = state.setValue(CONNECTIONS.get(direction), IrrigationConnectable.connects(neighbor, direction.getOpposite()));
        }
        return state;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return state.setValue(CONNECTIONS.get(direction), IrrigationConnectable.connects(neighbor, direction.getOpposite()));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int mask = 0;
        for (Direction direction : Direction.values()) {
            if (state.getValue(CONNECTIONS.get(direction))) mask |= 1 << direction.ordinal();
        }
        return SHAPES[mask];
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) {
            IrrigationManager.get(serverLevel).invalidateAround(pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            IrrigationManager.get(serverLevel).invalidateAround(pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FarmTooltips.append(tooltip, "copper_pipe");
    }
}
