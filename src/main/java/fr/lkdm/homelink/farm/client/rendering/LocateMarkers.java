package fr.lkdm.homelink.farm.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Temporary, client-only LOCATE markers: a colored outline around the block, a vertical beam
 * so it can be found from afar, and a few particles. Nothing is sent to the server and the
 * world is never modified. Markers beyond {@link #MAX_RENDER_DISTANCE} are not drawn.
 */
public final class LocateMarkers {
    private static final double MAX_RENDER_DISTANCE = 160;
    private static final int BEAM_HEIGHT = 12;
    private static final int MAX_MARKERS = 16;

    private record Marker(BlockPos pos, float red, float green, float blue, long expiresAt) {
    }

    private static final List<Marker> MARKERS = new ArrayList<>();

    private LocateMarkers() {
    }

    public static void add(BlockPos pos, int argb, int durationTicks) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        MARKERS.removeIf(marker -> marker.pos().equals(pos));
        if (MARKERS.size() >= MAX_MARKERS) MARKERS.removeFirst();
        MARKERS.add(new Marker(pos.immutable(), ((argb >> 16) & 0xFF) / 255F, ((argb >> 8) & 0xFF) / 255F,
                (argb & 0xFF) / 255F, level.getGameTime() + durationTicks));
    }

    public static List<BlockPos> active() {
        return MARKERS.stream().map(Marker::pos).toList();
    }

    public static void clear() {
        MARKERS.clear();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            MARKERS.clear();
            return;
        }
        long now = level.getGameTime();
        MARKERS.removeIf(marker -> marker.expiresAt() <= now);
        if (now % 4 != 0) return;
        for (Marker marker : MARKERS) {
            BlockPos pos = marker.pos();
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    (level.random.nextDouble() - 0.5) * 0.02, 0.06, (level.random.nextDouble() - 0.5) * 0.02);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Marker marker : MARKERS) {
            BlockPos pos = marker.pos();
            if (camera.distanceToSqr(Vec3.atCenterOf(pos)) > MAX_RENDER_DISTANCE * MAX_RENDER_DISTANCE) continue;
            AABB box = new AABB(pos).inflate(0.02);
            LevelRenderer.renderLineBox(pose, lines, box, marker.red(), marker.green(), marker.blue(), 1.0F);
            AABB beam = new AABB(pos.getX() + 0.45, pos.getY() + 1, pos.getZ() + 0.45,
                    pos.getX() + 0.55, pos.getY() + 1 + BEAM_HEIGHT, pos.getZ() + 0.55);
            LevelRenderer.renderLineBox(pose, lines, beam, marker.red(), marker.green(), marker.blue(), 0.8F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
