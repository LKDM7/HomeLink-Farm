package fr.lkdm.homelink.farm.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.farm.crop.CropZone;
import fr.lkdm.homelink.farm.item.FarmConnectorItem;
import fr.lkdm.homelink.farm.registry.ModDataComponents;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Client-only zone outlines:
 * <ul>
 *   <li>while holding a Farm Connector: the selected controller (green) and the zone being
 *   selected, from Position A to Position B, or to the targeted block until B is set (gold);</li>
 *   <li>after "Show zone" in a Crop Monitor screen: that monitor's current zone (cyan) for a few seconds.</li>
 * </ul>
 */
public final class ZonePreview {
    private static final int SHOW_TICKS = 200;
    private static final int MAX_SHOWN = 16;
    private static final Map<BlockPos, Long> SHOWN_MONITORS = new HashMap<>();

    private ZonePreview() {
    }

    /** Shows the zone of the monitor at {@code pos} for {@link #SHOW_TICKS} ticks. */
    public static void showMonitorZone(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            if (SHOWN_MONITORS.size() >= MAX_SHOWN && !SHOWN_MONITORS.containsKey(pos)) {
                SHOWN_MONITORS.entrySet().stream().min(Map.Entry.comparingByValue())
                        .ifPresent(oldest -> SHOWN_MONITORS.remove(oldest.getKey()));
            }
            SHOWN_MONITORS.put(pos.immutable(), level.getGameTime() + SHOW_TICKS);
        }
    }

    public static boolean showing(BlockPos monitor) {
        return SHOWN_MONITORS.containsKey(monitor);
    }

    public static void clear() {
        SHOWN_MONITORS.clear();
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) return;
        long now = client.level.getGameTime();
        double time = now + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        SHOWN_MONITORS.values().removeIf(expiry -> expiry <= now);
        ItemStack connector = heldConnector(client);
        if (connector.isEmpty() && SHOWN_MONITORS.isEmpty()) return;

        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        if (!connector.isEmpty()) {
            GlobalPos controller = connector.get(ModDataComponents.SELECTED_CONTROLLER.get());
            if (controller != null && controller.dimension().equals(client.level.dimension())) {
                float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(controller.pos())), 96);
                FieldGuide.corners(pose, lines, new AABB(controller.pos()).inflate(0.03), 0xA1BD92, fade);
            }
            GlobalPos a = connector.get(ModDataComponents.ZONE_CORNER_A.get());
            if (a != null && a.dimension().equals(client.level.dimension())) {
                GlobalPos b = connector.get(ModDataComponents.ZONE_CORNER_B.get());
                if (b != null && !b.dimension().equals(client.level.dimension())) b = null;
                BlockPos end = b != null ? b.pos() : targetedBlock(client);
                float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(a.pos())), 128);
                FieldGuide.corners(pose, lines, new AABB(a.pos()).inflate(0.02), FieldGuide.GOLD, fade);
                if (end != null) {
                    AABB zone = new CropZone(a.pos(), end).toAabb().inflate(0.01);
                    FieldGuide.zone(pose, lines, zone, FieldGuide.GOLD, fade * (b != null ? 1.0F : 0.65F), time);
                    FieldGuide.corners(pose, lines, new AABB(end).inflate(0.02), FieldGuide.GOLD, fade);
                }
            }
        }
        for (BlockPos monitorPos : SHOWN_MONITORS.keySet()) {
            if (client.level.getBlockEntity(monitorPos) instanceof CropMonitorBlockEntity monitor && monitor.zone().isPresent()) {
                float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(monitorPos)), 128);
                double remaining = SHOWN_MONITORS.get(monitorPos) - time;
                float envelope = (float)Math.clamp(Math.min((SHOW_TICKS - remaining) / 8, remaining / 25), 0, 1);
                FieldGuide.zone(pose, lines, monitor.zone().get().toAabb().inflate(0.02),
                        FieldGuide.WATER, fade * envelope, time);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static ItemStack heldConnector(Minecraft client) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = client.player.getItemInHand(hand);
            if (stack.getItem() instanceof FarmConnectorItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static BlockPos targetedBlock(Minecraft client) {
        return client.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }
}
