package fr.lkdm.homelink.farm.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmComponent;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class CropMonitorBlock extends AbstractFarmDeviceBlock {
    public static final MapCodec<CropMonitorBlock> CODEC = simpleCodec(CropMonitorBlock::new);
    /** Base plate, post and sensor head (the antenna stays out of the outline). */
    private static final Map<Direction, VoxelShape> SHAPES = DeviceShapes.horizontal(
            new double[] {3, 0, 3, 13, 1, 13},
            new double[] {7, 1, 7, 9, 7, 9},
            new double[] {2, 7, 4, 14, 16, 12});

    public CropMonitorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected Map<Direction, VoxelShape> shapes() {
        return SHAPES;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropMonitorBlockEntity(pos, state);
    }

    /** A freshly placed monitor watches the 9 x 9 area around itself until a zone is chosen. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof CropMonitorBlockEntity monitor && monitor.zone().isEmpty()) {
            monitor.applyDefaultZone();
        }
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Output chosen by the monitor's comparator mode (maturity by default: 0% -> 0, 100% -> 15). */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CropMonitorBlockEntity monitor ? monitor.comparatorSignal() : 0;
    }

    @Override
    protected void onBroken(ServerLevel level, AbstractFarmDeviceBlockEntity device) {
        if (device instanceof FarmComponent component) FarmLinkService.onComponentRemoved(level, component);
    }
}
