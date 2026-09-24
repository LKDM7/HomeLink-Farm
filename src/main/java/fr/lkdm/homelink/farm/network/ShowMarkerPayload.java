package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to one client: show a temporary, client-only marker on a block (never modifies the world). */
public record ShowMarkerPayload(BlockPos pos, int color, int durationTicks) implements CustomPacketPayload {
    public static final Type<ShowMarkerPayload> TYPE = new Type<>(HomeLinkFarm.id("show_marker"));
    public static final StreamCodec<ByteBuf, ShowMarkerPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ShowMarkerPayload::pos,
            ByteBufCodecs.INT, ShowMarkerPayload::color,
            ByteBufCodecs.VAR_INT, ShowMarkerPayload::durationTicks,
            ShowMarkerPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
