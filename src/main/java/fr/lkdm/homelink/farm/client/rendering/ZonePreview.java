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
import net.minecraft.client.renderer.LevelRenderer;
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
    private static final Map<BlockPos, Long> SHOWN_MONITORS = new HashMap<>();

    private ZonePreview() {
    }

    /** Shows the zone of the monitor at {@code pos} for {@link #SHOW_TICKS} ticks. */
    public static void showMonitorZone(BlockPos pos) {
        var level = Minecraft.getInstance().level;
        if (level != null) SHOWN_MONITORS.put(pos.immutable(), level.getGameTime() + SHOW_TICKS);
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
                LevelRenderer.renderLineBox(pose, lines, new AABB(controller.pos()).inflate(0.03), 0.35F, 0.85F, 0.35F, 1.0F);
            }
            GlobalPos a = connector.get(ModDataComponents.ZONE_CORNER_A.get());
            if (a != null && a.dimension().equals(client.level.dimension())) {
                GlobalPos b = connector.get(ModDataComponents.ZONE_CORNER_B.get());
                BlockPos end = b != null ? b.pos() : targetedBlock(client);
                LevelRenderer.renderLineBox(pose, lines, new AABB(a.pos()).inflate(0.02), 1.0F, 0.85F, 0.2F, 1.0F);
                if (end != null) {
                    AABB zone = new CropZone(a.pos(), end).toAabb().inflate(0.01);
                    LevelRenderer.renderLineBox(pose, lines, zone, 1.0F, 0.75F, 0.1F, b != null ? 1.0F : 0.6F);
                }
            }
        }
        for (BlockPos monitorPos : SHOWN_MONITORS.keySet()) {
            if (client.level.getBlockEntity(monitorPos) instanceof CropMonitorBlockEntity monitor && monitor.zone().isPresent()) {
                LevelRenderer.renderLineBox(pose, lines, monitor.zone().get().toAabb().inflate(0.02), 0.25F, 0.85F, 0.95F, 1.0F);
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
