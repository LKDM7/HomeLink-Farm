package fr.lkdm.homelink.farm.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.farm.blockentity.CopperSprinklerBlockEntity;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationConnectable;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Copper Sprinkler: an end point of a copper network, irrigating the area around it while its
 * network is ACTIVE. Standing (placed on top of a block) it connects through its bottom and
 * sides; hanging (placed against the underside of a pipe) it connects through its top and
 * sides and sprays downwards.
 */
public class CopperSprinklerBlock extends BaseEntityBlock implements IrrigationConnectable {
    public static final MapCodec<CopperSprinklerBlock> CODEC = simpleCodec(CopperSprinklerBlock::new);
    /** True when mounted under a pipe, head pointing down. */
    public static final BooleanProperty HANGING = BlockStateProperties.HANGING;
    /** Flange, riser, hub and the two nozzle arms. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(4, 0, 4, 12, 2, 12),
            Block.box(6.5, 2, 6.5, 9.5, 10, 9.5),
            Block.box(6, 3, 6, 10, 4, 10),
            Block.box(6, 8, 6, 10, 9, 10),
            Block.box(5, 10, 5, 11, 13, 11),
            Block.box(1, 10, 7, 15, 12, 9),
            Block.box(7, 10, 1, 9, 12, 15));
    private static final VoxelShape HANGING_SHAPE = Shapes.or(
            Block.box(4, 14, 4, 12, 16, 12),
            Block.box(6.5, 6, 6.5, 9.5, 14, 9.5),
            Block.box(6, 12, 6, 10, 13, 10),
            Block.box(6, 7, 6, 10, 8, 10),
            Block.box(5, 3, 5, 11, 6, 11),
            Block.box(1, 4, 7, 15, 6, 9),
            Block.box(7, 4, 1, 9, 6, 15));

    public CopperSprinklerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(IrrigationVisual.PROPERTY, IrrigationVisual.OFF).setValue(HANGING, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(IrrigationVisual.PROPERTY, HANGING);
    }

    @Override
    public NodeKind nodeKind() {
        return NodeKind.SPRINKLER;
    }

    @Override
    public boolean connectsIrrigation(BlockState state, Direction face) {
        return face != (isHanging(state) ? Direction.DOWN : Direction.UP);
    }

    public static boolean isHanging(BlockState state) {
        return state.hasProperty(HANGING) && state.getValue(HANGING);
    }

    /** Clicking the underside of a block (typically a pipe) mounts the sprinkler hanging below it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(HANGING, context.getClickedFace() == Direction.DOWN);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return isHanging(state) ? HANGING_SHAPE : SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CopperSprinklerBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CopperSprinklerBlockEntity sprinkler) sprinkler.serverTick((ServerLevel) tickLevel);
        };
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

    /** Client-side spray while irrigating (visual only). */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(IrrigationVisual.PROPERTY) != IrrigationVisual.ACTIVE) return;
        boolean hanging = isHanging(state);
        double nozzle = hanging ? 0.2 : 0.8;
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 0.12 + random.nextDouble() * 0.1;
            level.addParticle(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + nozzle, pos.getZ() + 0.5,
                    Math.cos(angle) * speed, hanging ? -0.05 : 0.2, Math.sin(angle) * speed);
        }
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.DRIPPING_WATER, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 3,
                    pos.getY() + (hanging ? 0.1 : 0.9), pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 3, 0, 0, 0);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        FarmTooltips.append(tooltip, "copper_sprinkler");
    }
}
