package fr.lkdm.homelink.farm.farm.diagnostic;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** One located problem found by a Crop Monitor scan. */
public record CropProblem(BlockPos pos, ProblemType type) {
    public static final StreamCodec<ByteBuf, CropProblem> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CropProblem::pos,
            ByteBufCodecs.VAR_INT.map(ProblemType::byId, ProblemType::ordinal), CropProblem::type,
            CropProblem::new);

    public CropProblem {
        pos = pos.immutable();
    }
}
