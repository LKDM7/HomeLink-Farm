package fr.lkdm.homelink.farm.client.overlay;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.farm.crop.CropAdapters;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationConnectable;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationCoverage;
import fr.lkdm.homelink.farm.network.IrrigationOverlayPayloads;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

/**
 * SHOW IRRIGATION: temporary client view of sprinkler coverage. While enabled it asks the
 * server for fresh data every {@link #REFRESH_TICKS} and draws, for sprinklers within
 * {@link #MAX_RENDER_DISTANCE}: the covered square (blue = irrigating, red = network fault,
 * grey = not fed) at the level of its crops, and boxes (orange: not irrigated, red: irrigation
 * offline) on uncovered crops reported by nearby Crop Monitors.
 * Only outlines and one flat quad per sprinkler are drawn, so large farms stay cheap.
 */
public final class IrrigationOverlay {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.homelink_farm.irrigation_overlay",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.homelink_farm");
    private static final int REFRESH_TICKS = 40;
    private static final double MAX_RENDER_DISTANCE = 96;
    /** Light tint so crops stay readable when standing inside a covered area. */
    private static final int FILL_ALPHA = 36;

    private static boolean enabled;
    private static long lastRequest = Long.MIN_VALUE;
    private static IrrigationOverlayPayloads.Data data = new IrrigationOverlayPayloads.Data(2, List.of(), List.of());

    private IrrigationOverlay() {
    }

    public static boolean enabled() {
        return enabled;
    }

    public static IrrigationOverlayPayloads.Data data() {
        return data;
    }

    public static void registerKey(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_KEY);
    }

    public static void toggle() {
        setEnabled(!enabled);
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        lastRequest = Long.MIN_VALUE;
        if (!value) data = new IrrigationOverlayPayloads.Data(data.range(), List.of(), List.of());
        var player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(value ? "message.homelink_farm.overlay.on" : "message.homelink_farm.overlay.off")
                    .withStyle(value ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
        }
    }

    public static void accept(IrrigationOverlayPayloads.Data payload) {
        if (enabled) data = payload;
    }

    public static void clear() {
        enabled = false;
        data = new IrrigationOverlayPayloads.Data(2, List.of(), List.of());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_KEY.consumeClick()) toggle();
        var level = Minecraft.getInstance().level;
        if (!enabled || level == null || Minecraft.getInstance().getConnection() == null) return;
        long now = level.getGameTime();
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= REFRESH_TICKS || now < lastRequest) {
            lastRequest = now;
            PacketDistributor.sendToServer(new IrrigationOverlayPayloads.Request());
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        if (data.sprinklers().isEmpty() && data.uncovered().isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        int range = data.range();
        VertexConsumer fill = buffers.getBuffer(RenderType.debugQuads());
        Matrix4f matrix = pose.last().pose();
        for (var mark : data.sprinklers()) {
            BlockPos pos = mark.pos();
            if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) continue;
            int color = color(mark);
            float y = groundLevel(pos) + 0.03F;
            float x0 = pos.getX() - range;
            float z0 = pos.getZ() - range;
            float x1 = pos.getX() + range + 1;
            float z1 = pos.getZ() + range + 1;
            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;
            fill.addVertex(matrix, x0, y, z0).setColor(r, g, b, FILL_ALPHA);
            fill.addVertex(matrix, x0, y, z1).setColor(r, g, b, FILL_ALPHA);
            fill.addVertex(matrix, x1, y, z1).setColor(r, g, b, FILL_ALPHA);
            fill.addVertex(matrix, x1, y, z0).setColor(r, g, b, FILL_ALPHA);
        }
        buffers.endBatch(RenderType.debugQuads());
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (var mark : data.sprinklers()) {
            BlockPos pos = mark.pos();
            if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) continue;
            int color = color(mark);
            float r = ((color >> 16) & 0xFF) / 255F;
            float g = ((color >> 8) & 0xFF) / 255F;
            float b = (color & 0xFF) / 255F;
            double ground = groundLevel(pos);
            LevelRenderer.renderLineBox(pose, lines, pos.getX() - range, ground, pos.getZ() - range,
                    pos.getX() + range + 1, ground + 0.06, pos.getZ() + range + 1, r, g, b, 1.0F);
            LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.03), r, g, b, 1.0F);
        }
        for (CropProblem problem : data.uncovered()) {
            BlockPos pos = problem.pos();
            if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) continue;
            int color = problem.type().color();
            LevelRenderer.renderLineBox(pose, lines, new AABB(pos).deflate(0.15),
                    ((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /**
     * Height at which the crops of a sprinkler stand: going down from the sprinkler, skip air,
     * crops and irrigation blocks (a sprinkler raised on a pipe, or hanging under one); the first
     * other block is the ground.
     */
    private static float groundLevel(BlockPos sprinkler) {
        var level = Minecraft.getInstance().level;
        if (level == null) return sprinkler.getY();
        for (int depth = 1; depth <= IrrigationCoverage.HANGING_BELOW + 1; depth++) {
            BlockPos pos = sprinkler.below(depth);
            var state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof IrrigationConnectable || CropAdapters.get(state) != null) continue;
            return pos.getY() + 1;
        }
        return sprinkler.getY();
    }

    private static int color(IrrigationOverlayPayloads.SprinklerMark mark) {
        return switch (mark.state()) {
            case ACTIVE -> 0x3AA8E8;
            case ERROR -> 0xE04040;
            case OFF -> 0x909090;
        };
    }
}
