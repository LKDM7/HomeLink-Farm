package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client request to locate a problem. The server only answers if the player has this monitor
 * open and the position is one of the monitor's current problems (no arbitrary lookups).
 */
public record LocateProblemPayload(BlockPos monitor, BlockPos problem) implements CustomPacketPayload {
    public static final Type<LocateProblemPayload> TYPE = new Type<>(HomeLinkFarm.id("locate_problem"));
    public static final StreamCodec<ByteBuf, LocateProblemPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LocateProblemPayload::monitor,
            BlockPos.STREAM_CODEC, LocateProblemPayload::problem,
            LocateProblemPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Server-side authorization: the player must have this monitor's screen open (and be in
     * range), and the position must be one of the monitor's current problems.
     */
    public static Optional<CropProblem> authorize(ServerPlayer player, BlockPos monitorPos, BlockPos problemPos) {
        if (!(player.containerMenu instanceof FarmDeviceMenu menu) || !menu.pos().equals(monitorPos) || !menu.stillValid(player)) {
            return Optional.empty();
        }
        if (!(player.level().getBlockEntity(monitorPos) instanceof CropMonitorBlockEntity monitor)) return Optional.empty();
        return monitor.findProblem(problemPos);
    }

    public static void handle(LocateProblemPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof FarmDeviceMenu menu) || !menu.pos().equals(payload.monitor())) return;
        Optional<CropProblem> problem = authorize(player, payload.monitor(), payload.problem());
        if (problem.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.homelink_farm.locate.resolved").withStyle(ChatFormatting.YELLOW), true);
            return;
        }
        CropProblem located = problem.get();
        PacketDistributor.sendToPlayer(player, new ShowMarkerPayload(located.pos(), located.type().color(),
                FarmServerConfig.LOCATE_DURATION.get()));
        BlockPos pos = located.pos();
        int distance = (int) Math.sqrt(player.blockPosition().distSqr(pos));
        player.displayClientMessage(Component.translatable("message.homelink_farm.locate.shown", located.type().label(),
                pos.getX(), pos.getY(), pos.getZ(), distance).withStyle(ChatFormatting.AQUA), false);
    }
}
