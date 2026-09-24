package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.farm.FarmAccess;
import fr.lkdm.homelink.farm.farm.controller.LinkResult;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Command sent by a device screen. The server checks that the player really has this
 * device's screen open and in range, then its rights for configuration commands.
 */
public record DeviceCommandPayload(BlockPos pos, int command, int argument) implements CustomPacketPayload {
    public static final Type<DeviceCommandPayload> TYPE = new Type<>(HomeLinkFarm.id("device_command"));
    public static final StreamCodec<ByteBuf, DeviceCommandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DeviceCommandPayload::pos,
            ByteBufCodecs.VAR_INT, DeviceCommandPayload::command,
            ByteBufCodecs.VAR_INT, DeviceCommandPayload::argument,
            DeviceCommandPayload::new);

    public DeviceCommandPayload(BlockPos pos, DeviceCommand command, int argument) {
        this(pos, command.ordinal(), argument);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public enum Decision { ALLOWED, NO_OPEN_SCREEN, NOT_A_DEVICE, NO_PERMISSION }

    /**
     * Server-side authorization: the player must have THIS device's screen open and be in range;
     * configuration commands additionally require manage rights ({@link FarmAccess}).
     */
    public static Decision authorize(ServerPlayer player, BlockPos pos, boolean modifiesConfiguration) {
        if (!(player.containerMenu instanceof FarmDeviceMenu menu) || !menu.pos().equals(pos) || !menu.stillValid(player)) {
            return Decision.NO_OPEN_SCREEN;
        }
        var blockEntity = player.level().getBlockEntity(pos);
        if (!(blockEntity instanceof AbstractFarmDeviceBlockEntity device) || !(blockEntity instanceof DeviceCommandTarget)) return Decision.NOT_A_DEVICE;
        if (modifiesConfiguration && !FarmAccess.canManage(player, device)) return Decision.NO_PERMISSION;
        return Decision.ALLOWED;
    }

    public static void handle(DeviceCommandPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        DeviceCommand command = DeviceCommand.byId(payload.command());
        if (command == null) return;
        Decision decision = authorize(player, payload.pos(), command.modifiesConfiguration());
        if (decision != Decision.ALLOWED) {
            HomeLinkFarm.LOGGER.debug("Refused {} from {} at {}: {}", command, player.getGameProfile().getName(), payload.pos(), decision);
            if (decision == Decision.NO_PERMISSION) {
                player.displayClientMessage(LinkResult.NO_PERMISSION.message().withStyle(ChatFormatting.RED), true);
            }
            return;
        }
        ((DeviceCommandTarget) player.level().getBlockEntity(payload.pos())).handleCommand(player, command, payload.argument());
    }
}
