package fr.lkdm.homelink.farm.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmBotStationBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.FarmLinkService;
import fr.lkdm.homelink.farm.homelink.HomeNetworkBinding;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * FarmBot Station. Its FACING side is the dock (the robot parks on the block in front of it);
 * the opposite side is the rear STORAGE OUTPUT port.
 */
public class FarmBotStationBlock extends AbstractFarmDeviceBlock {
    public static final MapCodec<FarmBotStationBlock> CODEC = simpleCodec(FarmBotStationBlock::new);
    /** Base plate, charging tower, roof, the two front charging contacts and the rear port. */
    private static final Map<Direction, VoxelShape> SHAPES = DeviceShapes.horizontal(
            new double[] {0, 0, 0, 16, 2, 16},
            new double[] {2, 2, 6, 14, 12, 15},
            new double[] {1, 12, 5, 15, 14, 16},
            new double[] {3, 2, 3, 6, 5, 6},
            new double[] {10, 2, 3, 13, 5, 6},
            new double[] {5, 4, 15, 11, 10, 16});

    public FarmBotStationBlock(Properties properties) {
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
        return new FarmBotStationBlockEntity(pos, state);
    }

    @Override
    protected void onBroken(ServerLevel level, AbstractFarmDeviceBlockEntity device) {
        if (device instanceof FarmBotStationBlockEntity station) {
            station.onBroken(level);
            FarmLinkService.onComponentRemoved(level, station);
        }
        HomeNetworkBinding.forgetOnRemoval(level, device);
    }
}
