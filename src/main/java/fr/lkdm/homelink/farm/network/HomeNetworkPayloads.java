package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.AbstractFarmDeviceBlockEntity;
import fr.lkdm.homelink.farm.homelink.HomeNetworkBinding;
import fr.lkdm.homelink.farm.menu.FarmDeviceMenu;
import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Choosing the HomeCore HomeNetwork of a Farm Controller or an Irrigation Pump from its screen. */
public final class HomeNetworkPayloads {
    public static final int MAX_CHOICES = 32;

    private HomeNetworkPayloads() {
    }

    public record Choice(UUID id, String name) {
        public static final StreamCodec<ByteBuf, Choice> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Choice::id, ByteBufCodecs.stringUtf8(128), Choice::name, Choice::new);
    }

    /** Server to client: networks the player may attach this device to (MANAGE_NETWORK only). */
    public record Choices(BlockPos device, List<Choice> choices) implements CustomPacketPayload {
        public static final Type<Choices> TYPE = new Type<>(HomeLinkFarm.id("network_choices"));
        public static final StreamCodec<ByteBuf, Choices> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Choices::device, Choice.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHOICES)), Choices::choices,
                Choices::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client to server: attach the open device to a network (empty = detach). */
    public record Bind(BlockPos device, Optional<UUID> network) implements CustomPacketPayload {
        public static final Type<Bind> TYPE = new Type<>(HomeLinkFarm.id("bind_network"));
        public static final StreamCodec<ByteBuf, Bind> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Bind::device, ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), Bind::network, Bind::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void sendChoices(ServerPlayer player, AbstractFarmDeviceBlockEntity device) {
        List<Choice> choices = HomeNetworkBinding.manageableNetworks(player).stream()
                .limit(MAX_CHOICES).map(network -> new Choice(network.id(), network.name())).toList();
        PacketDistributor.sendToPlayer(player, new Choices(device.getBlockPos(), choices));
    }

    public static void handleBind(Bind payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        if (!(player.containerMenu instanceof FarmDeviceMenu menu) || !menu.pos().equals(payload.device()) || !menu.stillValid(player)) return;
        if (!(player.level().getBlockEntity(payload.device()) instanceof AbstractFarmDeviceBlockEntity device)) return;
        HomeNetworkBinding.Result result = HomeNetworkBinding.bind(player, device, payload.network());
        boolean ok = result == HomeNetworkBinding.Result.BOUND || result == HomeNetworkBinding.Result.UNBOUND
                || result == HomeNetworkBinding.Result.UNCHANGED;
        player.displayClientMessage(Component.translatable("message.homelink_farm.network." + result.name().toLowerCase(Locale.ROOT),
                device.homeNetworkName()).withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED), true);
    }
}
