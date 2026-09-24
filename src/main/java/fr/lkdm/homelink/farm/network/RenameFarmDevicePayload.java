package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.farm.controller.LinkResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client request to rename the device whose screen is open. Validated entirely server-side. */
public record RenameFarmDevicePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<RenameFarmDevicePayload> TYPE = new Type<>(HomeLinkFarm.id("rename_device"));
    /** Generous wire limit; the server sanitizes and truncates to {@code DeviceNames.MAX_LENGTH}. */
    private static final int WIRE_MAX_LENGTH = 64;
    public static final StreamCodec<ByteBuf, RenameFarmDevicePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RenameFarmDevicePayload::pos,
            ByteBufCodecs.stringUtf8(WIRE_MAX_LENGTH), RenameFarmDevicePayload::name,
            RenameFarmDevicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Runs on the server thread. */
    public static void handle(RenameFarmDevicePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        DeviceCommandPayload.Decision decision = DeviceCommandPayload.authorize(player, payload.pos(), true);
        if (decision == DeviceCommandPayload.Decision.NO_PERMISSION) {
            player.displayClientMessage(LinkResult.NO_PERMISSION.message().withStyle(ChatFormatting.RED), true);
            return;
        }
        if (decision != DeviceCommandPayload.Decision.ALLOWED
                || !(player.level().getBlockEntity(payload.pos()) instanceof AbstractFarmDeviceBlockEntity device)) {
            return;
        }
        device.setCustomName(payload.name());
    }
}
