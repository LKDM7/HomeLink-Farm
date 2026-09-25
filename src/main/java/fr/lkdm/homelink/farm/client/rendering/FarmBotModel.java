package fr.lkdm.homelink.farm.client.rendering;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.config.FarmServerConfig;
import fr.lkdm.homelink.farm.entity.FarmBotEntity;
import fr.lkdm.homelink.farm.farm.bot.FarmBotState;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;

/**
 * Voxel model of the FarmBot, a small robot mower (front towards -Z): anthracite skirt, light grey
 * body with a copper stripe, low white front hood with a sensor dome, raised white storage shell
 * with a control panel and stop button, copper bumper and headlights, cutter housing with a
 * star-shaped harvesting reel, big rear drive wheels and small front casters, a copper charging
 * plate and a red light at the back, a spinning lidar and a status light on its antenna.
 * <p>
 * Animations: wheels and casters roll with the distance driven, the lidar spins while the robot
 * works, the reel spins and the body shakes while harvesting, and the lights follow the state.
 */
public class FarmBotModel extends EntityModel<FarmBotEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(HomeLinkFarm.id("farmbot"), "main");

    private final ModelPart root;
    private final ModelPart body;
    private final ModelPart statusLight;
    private final ModelPart lamps;
    private final ModelPart lidar;
    private final ModelPart reel;
    private final ModelPart[] wheels;
    private final ModelPart[] casters;
    private int statusColor = 0xFFFFFFFF;
    private boolean powered;

    public FarmBotModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body");
        statusLight = body.getChild("status_light");
        lamps = body.getChild("lamps");
        lidar = body.getChild("lidar");
        reel = root.getChild("reel");
        wheels = new ModelPart[] {root.getChild("wheel_left"), root.getChild("wheel_right")};
        casters = new ModelPart[] {root.getChild("caster_left"), root.getChild("caster_right")};
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();
        PartDefinition body = parts.addOrReplaceChild("body", CubeListBuilder.create()
                // Chassis skirt and lower body.
                .texOffs(0, 0).addBox(-6.0F, -4.0F, -8.0F, 12, 2, 16)
                .texOffs(0, 18).addBox(-6.5F, -7.0F, -7.0F, 13, 3, 14)
                // Raised storage shell at the back, low hood at the front, sensor dome.
                .texOffs(56, 0).addBox(-6.0F, -10.0F, -2.0F, 12, 3, 9)
                .texOffs(56, 12).addBox(-5.5F, -8.0F, -7.5F, 11, 1, 6)
                .texOffs(108, 19).addBox(-2.0F, -9.0F, -6.0F, 4, 1, 3)
                // Control panel and red stop button on the shell.
                .texOffs(0, 35).addBox(0.0F, -11.0F, 1.0F, 5, 1, 4)
                .texOffs(20, 35).addBox(3.0F, -12.0F, 2.0F, 1, 1, 1)
                // Copper bumper, cutter housing, charging plate, antenna.
                .texOffs(24, 35).addBox(-6.0F, -5.0F, -9.0F, 12, 2, 1)
                .texOffs(56, 19).addBox(-5.5F, -3.0F, -10.0F, 11, 2, 2)
                .texOffs(50, 35).addBox(-3.0F, -6.0F, 7.0F, 6, 2, 1)
                .texOffs(40, 40).addBox(4.0F, -14.0F, 5.5F, 1, 4, 1), PartPose.offset(0, 24, 0));
        body.addOrReplaceChild("status_light", CubeListBuilder.create().texOffs(44, 40).addBox(-1, -1, -1, 2, 2, 2),
                PartPose.offset(4.5F, -15.0F, 6.0F));
        body.addOrReplaceChild("lamps", CubeListBuilder.create()
                .texOffs(82, 35).addBox(-5.0F, -6.0F, -8.0F, 2, 1, 1)
                .texOffs(82, 35).addBox(3.0F, -6.0F, -8.0F, 2, 1, 1)
                .texOffs(64, 35).addBox(-4.0F, -9.0F, 7.0F, 8, 1, 1), PartPose.ZERO);
        body.addOrReplaceChild("lidar", CubeListBuilder.create().texOffs(28, 40).addBox(-1.5F, -1.0F, -1.5F, 3, 1, 3),
                PartPose.offset(-3.0F, -10.0F, 3.0F));
        parts.addOrReplaceChild("wheel_left", CubeListBuilder.create().texOffs(0, 40).addBox(-1, -2.5F, -2.5F, 2, 5, 5),
                PartPose.offset(-6.5F, 21.5F, 4.5F));
        parts.addOrReplaceChild("wheel_right", CubeListBuilder.create().texOffs(0, 40).addBox(-1, -2.5F, -2.5F, 2, 5, 5),
                PartPose.offset(6.5F, 21.5F, 4.5F));
        parts.addOrReplaceChild("caster_left", CubeListBuilder.create().texOffs(20, 40).addBox(-0.5F, -1.5F, -1.5F, 1, 3, 3),
                PartPose.offset(-4.5F, 22.5F, -5.0F));
        parts.addOrReplaceChild("caster_right", CubeListBuilder.create().texOffs(20, 40).addBox(-0.5F, -1.5F, -1.5F, 1, 3, 3),
                PartPose.offset(4.5F, 22.5F, -5.0F));
        parts.addOrReplaceChild("reel", CubeListBuilder.create()
                .texOffs(84, 19).addBox(-5, -1, -1, 10, 2, 2)
                .texOffs(84, 23).addBox(-5, -1.5F, -0.5F, 10, 3, 1)
                .texOffs(106, 23).addBox(-5, -0.5F, -1.5F, 10, 1, 3), PartPose.offset(0, 22.5F, -11.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(FarmBotEntity bot, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        FarmBotState state = bot.displayedState();
        // Same ground speed for both: the small casters turn twice as fast as the drive wheels.
        for (ModelPart wheel : wheels) wheel.xRot = limbSwing * 1.2F;
        for (ModelPart caster : casters) caster.xRot = limbSwing * 2.0F;
        boolean harvesting = state == FarmBotState.HARVESTING;
        reel.xRot = harvesting ? ageInTicks * 0.9F : limbSwing * 2.0F;
        body.y = 24 + (harvesting ? Mth.sin(ageInTicks * 1.7F) * 0.25F : 0);
        powered = state != FarmBotState.OUT_OF_POWER;
        boolean working = powered && !state.isFault() && state != FarmBotState.PAUSED && state != FarmBotState.IDLE
                && state != FarmBotState.DOCKED && state != FarmBotState.CHARGING && state != FarmBotState.UNLOADING;
        lidar.yRot = working ? ageInTicks * 0.35F : lidar.yRot;
        boolean blink = Mth.floor(ageInTicks / 8) % 2 == 0;
        statusColor = switch (state) {
            case OUT_OF_POWER -> 0xFF303234;
            case ERROR, STUCK, OUTPUT_BLOCKED -> blink ? 0xFFE0473C : 0xFF5A2320;
            case LOW_BATTERY, STORAGE_FULL -> blink ? 0xFFF0B040 : 0xFF6A5020;
            case PAUSED -> 0xFFF0B040;
            case CHARGING -> pulse(0xA1, 0xE0, 0x7A, ageInTicks);
            case HARVESTING, UNLOADING -> 0xFFF09A50;
            case DOCKED, IDLE -> 0xFFE8E8E0;
            default -> bot.displayedBattery() <= FarmServerConfig.FARMBOT_LOW_BATTERY_THRESHOLD.get() && blink ? 0xFFF0B040 : 0xFF6FD3E0;
        };
    }

    private static int pulse(int red, int green, int blue, float ageInTicks) {
        float level = 0.65F + 0.35F * Mth.sin(ageInTicks * 0.15F);
        return 0xFF000000 | Math.round(red * level) << 16 | Math.round(green * level) << 8 | Math.round(blue * level);
    }

    @Override
    public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        // Lights are drawn separately: they glow (full brightness) while the robot has power.
        statusLight.visible = false;
        lamps.visible = false;
        root.render(pose, buffer, packedLight, packedOverlay, color);
        statusLight.visible = true;
        lamps.visible = true;
        pose.pushPose();
        body.translateAndRotate(pose);
        int glow = powered ? LightTexture.FULL_BRIGHT : packedLight;
        statusLight.render(pose, buffer, glow, packedOverlay, statusColor);
        lamps.render(pose, buffer, glow, packedOverlay, powered ? color : 0xFF505050);
        pose.popPose();
    }
}
