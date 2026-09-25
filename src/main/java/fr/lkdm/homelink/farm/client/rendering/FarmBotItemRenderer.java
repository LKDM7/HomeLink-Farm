package fr.lkdm.homelink.farm.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the FarmBot item with the robot's own 3D model (hand, inventory, ground, item frame).
 * The item model ({@code builtin/entity}) only holds the per-context transforms.
 */
public class FarmBotItemRenderer extends BlockEntityWithoutLevelRenderer {
    /** The robot is about 1.3 blocks long: scaled to fit one block. */
    private static final float SCALE = 0.75F;
    @Nullable
    private FarmBotModel model;

    public FarmBotItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static IClientItemExtensions extensions() {
        return new IClientItemExtensions() {
            private FarmBotItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new FarmBotItemRenderer();
                return renderer;
            }
        };
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (model == null) model = new FarmBotModel(Minecraft.getInstance().getEntityModels().bakeLayer(FarmBotModel.LAYER));
        model.setupItem();
        pose.pushPose();
        // Same orientation as the entity renderer: model Y down, front towards +Z (the viewer).
        pose.translate(0.5, 0.0, 0.5);
        pose.scale(SCALE, SCALE, SCALE);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.translate(0.0, -1.5, 0.0);
        model.renderToBuffer(pose, buffers.getBuffer(model.renderType(FarmBotRenderer.TEXTURE)), light, overlay, 0xFFFFFFFF);
        pose.popPose();
    }
}
