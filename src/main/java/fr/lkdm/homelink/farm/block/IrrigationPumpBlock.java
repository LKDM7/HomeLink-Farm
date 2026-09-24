package fr.lkdm.homelink.farm.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmComponent;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationConnectable;
import fr.lkdm.homelink.farm.homelink.HomeNetworkBinding;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Hydraulic heart of a network: needs a water source next to it (or in it, when placed
 * underwater), feeds at most 5 sprinklers.
 */
public class IrrigationPumpBlock extends AbstractFarmDeviceBlock implements IrrigationConnectable, SimpleWaterloggedBlock {
    public static final MapCodec<IrrigationPumpBlock> CODEC = simpleCodec(IrrigationPumpBlock::new);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** Base plate, motor housing with its front panel and side grilles, top outlet. */
    private static final Map<Direction, VoxelShape> SHAPES = DeviceShapes.horizontal(
            new double[] {1, 0, 1, 15, 2, 15},
            new double[] {2.5, 2, 1, 13.5, 12, 13},
            new double[] {4.5, 12, 4.5, 11.5, 16, 11.5});

    public IrrigationPumpBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(IrrigationVisual.PROPERTY, IrrigationVisual.OFF).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(IrrigationVisual.PROPERTY, WATERLOGGED);
    }

    @Override
    protected Map<Direction, VoxelShape> shapes() {
        return SHAPES;
    }

    /** Placed in a water source, the pump is submerged and draws from the water around it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return super.getStateForPlacement(context).setValue(WATERLOGGED, water);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level,
                                     BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.PUMP;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IrrigationPumpBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock()) && level instanceof ServerLevel serverLevel) {
            IrrigationManager.get(serverLevel).invalidateAround(pos);
        }
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof IrrigationPumpBlockEntity pump) pump.onNeighborChanged();
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** See {@link fr.lkdm.homelink.farm.farm.irrigation.PumpStatus#comparatorSignal}. */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof IrrigationPumpBlockEntity pump ? pump.comparatorSignal() : 0;
    }

    @Override
    protected void onBroken(ServerLevel level, AbstractFarmDeviceBlockEntity device) {
        IrrigationManager.get(level).invalidateAround(device.getBlockPos());
        if (device instanceof FarmComponent component) FarmLinkService.onComponentRemoved(level, component);
        HomeNetworkBinding.forgetOnRemoval(level, device);
    }
}
