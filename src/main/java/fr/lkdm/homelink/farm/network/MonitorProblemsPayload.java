package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client: located problems of one Crop Monitor. Sent only to players who have that
 * monitor's screen open, when it opens and when the list changes.
 */
public record MonitorProblemsPayload(BlockPos monitor, List<CropProblem> problems) implements CustomPacketPayload {
    public static final Type<MonitorProblemsPayload> TYPE = new Type<>(HomeLinkFarm.id("monitor_problems"));
    public static final StreamCodec<ByteBuf, MonitorProblemsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MonitorProblemsPayload::monitor,
            CropProblem.STREAM_CODEC.apply(ByteBufCodecs.list(CropScanResult.MAX_SAMPLES)), MonitorProblemsPayload::problems,
            MonitorProblemsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
