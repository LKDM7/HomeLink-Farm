package fr.lkdm.homelink.farm.block;

import com.mojang.serialization.MapCodec;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
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

public class FarmControllerBlock extends AbstractFarmDeviceBlock {
    public static final MapCodec<FarmControllerBlock> CODEC = simpleCodec(FarmControllerBlock::new);
    /** Cabinet, button deck and screen. */
    private static final Map<Direction, VoxelShape> SHAPES = DeviceShapes.horizontal(
            new double[] {1, 0, 1, 15, 9, 15},
            new double[] {1, 9, 1, 15, 10, 8},
            new double[] {2, 9, 9, 14, 16, 14});

    public FarmControllerBlock(Properties properties) {
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
        return new FarmControllerBlockEntity(pos, state);
    }

    @Override
    protected void onBroken(ServerLevel level, AbstractFarmDeviceBlockEntity device) {
        if (device instanceof FarmControllerBlockEntity controller) FarmLinkService.onControllerRemoved(level, controller);
        HomeNetworkBinding.forgetOnRemoval(level, device);
    }
}
