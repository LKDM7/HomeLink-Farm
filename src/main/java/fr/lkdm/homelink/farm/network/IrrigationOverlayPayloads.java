package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.farm.crop.LoadedMonitors;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Irrigation overlay data. The client asks (at most once per second is honored); the server
 * answers with the sprinklers and uncovered crops around THAT player only, computed from its
 * own state. Nothing the client sends can change irrigation.
 */
public final class IrrigationOverlayPayloads {
    public static final int HORIZONTAL_RANGE = 64;
    public static final int VERTICAL_RANGE = 24;
    public static final int MAX_SPRINKLERS = 256;
    public static final int MAX_UNCOVERED = 512;
    private static final int MIN_REQUEST_INTERVAL = 20;
    private static final Map<ServerPlayer, Long> LAST_REQUEST = new WeakHashMap<>();

    private IrrigationOverlayPayloads() {
    }

    /** Client to server: "send me the irrigation overlay around me". */
    public record Request() implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(HomeLinkFarm.id("irrigation_overlay_request"));
        public static final StreamCodec<ByteBuf, Request> STREAM_CODEC = StreamCodec.unit(new Request());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record SprinklerMark(BlockPos pos, int visual) {
        public static final StreamCodec<ByteBuf, SprinklerMark> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, SprinklerMark::pos, ByteBufCodecs.VAR_INT, SprinklerMark::visual, SprinklerMark::new);

        public IrrigationVisual state() {
            IrrigationVisual[] values = IrrigationVisual.values();
            return values[Math.floorMod(visual, values.length)];
        }
    }

    /** Server to client: overlay snapshot around the player. */
    public record Data(int range, List<SprinklerMark> sprinklers, List<CropProblem> uncovered) implements CustomPacketPayload {
        public static final Type<Data> TYPE = new Type<>(HomeLinkFarm.id("irrigation_overlay"));
        public static final StreamCodec<ByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::range,
                SprinklerMark.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SPRINKLERS)), Data::sprinklers,
                CropProblem.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_UNCOVERED)), Data::uncovered,
                Data::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void handleRequest(Request request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long last = LAST_REQUEST.get(player);
        if (last != null && now - last < MIN_REQUEST_INTERVAL && now >= last) return;
        LAST_REQUEST.put(player, now);
        PacketDistributor.sendToPlayer(player, collect(level, player.blockPosition()));
    }

    /** Builds the overlay around {@code center} from server state. */
    public static Data collect(ServerLevel level, BlockPos center) {
        List<SprinklerMark> sprinklers = new ArrayList<>();
        for (BlockPos pos : IrrigationManager.get(level).loadedSprinklers()) {
            if (sprinklers.size() >= MAX_SPRINKLERS) break;
            if (!near(center, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.hasProperty(IrrigationVisual.PROPERTY)) sprinklers.add(new SprinklerMark(pos, state.getValue(IrrigationVisual.PROPERTY).ordinal()));
        }
        List<CropProblem> uncovered = new ArrayList<>();
        for (BlockPos monitorPos : LoadedMonitors.in(level)) {
            if (!near(center, monitorPos) || !(level.getBlockEntity(monitorPos) instanceof CropMonitorBlockEntity monitor)) continue;
            monitor.result().ifPresent(result -> {
                for (CropProblem problem : result.samples()) {
                    if (uncovered.size() >= MAX_UNCOVERED) return;
                    if (problem.type() == ProblemType.NOT_IRRIGATED || problem.type() == ProblemType.IRRIGATION_OFFLINE) uncovered.add(problem);
                }
            });
        }
        return new Data(FarmServerConfig.SPRINKLER_RANGE.get(), sprinklers, uncovered);
    }

    private static boolean near(BlockPos center, BlockPos pos) {
        return Math.abs(pos.getX() - center.getX()) <= HORIZONTAL_RANGE && Math.abs(pos.getZ() - center.getZ()) <= HORIZONTAL_RANGE
                && Math.abs(pos.getY() - center.getY()) <= VERTICAL_RANGE;
    }
}
