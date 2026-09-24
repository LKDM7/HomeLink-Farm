package fr.lkdm.homelink.farm.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Temporary, client-only LOCATE markers: crop corner marks, a floating diamond
 * and a few rising particles. Nothing is sent to the server and the
 * world is never modified. Markers beyond {@link #MAX_RENDER_DISTANCE} are not drawn.
 */
public final class LocateMarkers {
    private static final double MAX_RENDER_DISTANCE = 160;
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
        if (now % 8 != 0) return;
        for (Marker marker : MARKERS) {
            BlockPos pos = marker.pos();
            if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.distanceToSqr(Vec3.atCenterOf(pos)) > 48 * 48) continue;
            level.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5,
                    (level.random.nextDouble() - 0.5) * 0.02, 0.06, (level.random.nextDouble() - 0.5) * 0.02);
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MARKERS.isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        double time = level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Marker marker : MARKERS) {
            BlockPos pos = marker.pos();
            float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(pos)), MAX_RENDER_DISTANCE);
            fade *= (float)Math.clamp((marker.expiresAt() - time) / 25, 0, 1);
            if (fade <= 0) continue;
            AABB box = new AABB(pos).inflate(0.02);
            int color = ((int)(marker.red()*255)<<16) | ((int)(marker.green()*255)<<8) | (int)(marker.blue()*255);
            FieldGuide.corners(pose, lines, box, color, fade*.8F);
            double x=pos.getX()+.5, z=pos.getZ()+.5;
            double y=pos.getY()+1.8+Math.sin(time*.065)*.09;
            // Billboard diamond: readable from any direction, with a short locator stem.
            double dx=camera.x-x, dz=camera.z-z, length=Math.max(.001,Math.sqrt(dx*dx+dz*dz));
            double rx=dz/length*.2, rz=-dx/length*.2;
            FieldGuide.line(pose,lines,x,y+.25,z,x+rx,y,z+rz,color,fade);
            FieldGuide.line(pose,lines,x+rx,y,z+rz,x,y-.25,z,color,fade);
            FieldGuide.line(pose,lines,x,y-.25,z,x-rx,y,z-rz,color,fade);
            FieldGuide.line(pose,lines,x-rx,y,z-rz,x,y+.25,z,color,fade);
            FieldGuide.line(pose,lines,x,pos.getY()+1.05,z,x,y-.35,z,color,fade*.35F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
