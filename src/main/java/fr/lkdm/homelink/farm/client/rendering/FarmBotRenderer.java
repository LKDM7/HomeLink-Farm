package fr.lkdm.homelink.farm.client.rendering;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

public class FarmBotRenderer extends MobRenderer<FarmBotEntity, FarmBotModel> {
    private static final ResourceLocation TEXTURE = HomeLinkFarm.id("textures/entity/farmbot.png");

    public FarmBotRenderer(EntityRendererProvider.Context context) {
        super(context, new FarmBotModel(context.bakeLayer(FarmBotModel.LAYER)), 0.45F);
    }

    @Override
    public ResourceLocation getTextureLocation(FarmBotEntity bot) {
        return TEXTURE;
    }
}
