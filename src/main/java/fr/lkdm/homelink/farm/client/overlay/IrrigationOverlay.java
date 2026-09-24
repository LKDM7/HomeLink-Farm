package fr.lkdm.homelink.farm.client.overlay;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lkdm.homelink.farm.block.CopperSprinklerBlock;
import fr.lkdm.homelink.farm.client.rendering.FieldGuide;
import fr.lkdm.homelink.farm.farm.crop.CropAdapters;
import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationConnectable;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationCoverage;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationVisual;
import fr.lkdm.homelink.farm.network.IrrigationOverlayPayloads;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/** Terrain-following survey marks. Surface sampling is budgeted per tick, never per frame. */
public final class IrrigationOverlay {
    public static final KeyMapping TOGGLE_KEY = new KeyMapping("key.homelink_farm.irrigation_overlay",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_I, "key.categories.homelink_farm");
    private static final int REFRESH_TICKS = 40;
    private static final double MAX_RENDER_DISTANCE = 96;
    private static boolean enabled;
    private static long lastRequest = Long.MIN_VALUE;
    private static IrrigationOverlayPayloads.Data data = new IrrigationOverlayPayloads.Data(2, List.of(), List.of());
    private record Surface(BlockPos pos, double height, IrrigationVisual state) { }
    private static Map<BlockPos, Surface> surfaces = Map.of();
    private static List<CropProblem> nearbyProblems = List.of();
    private static final Map<BlockPos, Surface> building = new HashMap<>();
    private static final ArrayDeque<IrrigationOverlayPayloads.SprinklerMark> pending = new ArrayDeque<>();

    private IrrigationOverlay() { }
    public static boolean enabled() { return enabled; }
    public static IrrigationOverlayPayloads.Data data() { return data; }
    public static void registerKey(RegisterKeyMappingsEvent event) { event.register(TOGGLE_KEY); }
    public static void toggle() { setEnabled(!enabled); }

    public static void setEnabled(boolean value) {
        if (!value) clear();
        enabled = value;
        lastRequest = Long.MIN_VALUE;
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(value
                ? "message.homelink_farm.overlay.on" : "message.homelink_farm.overlay.off")
                .withStyle(value ? ChatFormatting.AQUA : ChatFormatting.GRAY), true);
    }

    public static void accept(IrrigationOverlayPayloads.Data payload) {
        if (!enabled) return;
        data = payload;
        updateNearbyProblems();
        pending.clear();
        building.clear();
        pending.addAll(payload.sprinklers());
        if (pending.isEmpty()) surfaces = Map.of();
    }

    public static void clear() {
        enabled = false;
        lastRequest = Long.MIN_VALUE;
        data = new IrrigationOverlayPayloads.Data(2, List.of(), List.of());
        surfaces = Map.of();
        nearbyProblems = List.of();
        building.clear();
        pending.clear();
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        while (TOGGLE_KEY.consumeClick()) toggle();
        var level = Minecraft.getInstance().level;
        if (!enabled || level == null || Minecraft.getInstance().getConnection() == null) return;
        long now = level.getGameTime();
        if (now % 10 == 0) updateNearbyProblems();
        if (lastRequest == Long.MIN_VALUE || now - lastRequest >= REFRESH_TICKS || now < lastRequest) {
            lastRequest = now;
            PacketDistributor.sendToServer(new IrrigationOverlayPayloads.Request());
        }
        // At most eight local footprints per tick, even with the maximum 256 sprinklers.
        boolean changed = !pending.isEmpty();
        for (int i = 0; i < 8 && !pending.isEmpty(); i++) sample(pending.removeFirst());
        if (changed && pending.isEmpty()) surfaces = Map.copyOf(building);
    }

    private static int priority(IrrigationVisual state) {
        return switch (state) { case ACTIVE -> 2; case ERROR -> 1; case OFF -> 0; };
    }

    private static void updateNearbyProblems() {
        var player = Minecraft.getInstance().player;
        if (player == null) { nearbyProblems = List.of(); return; }
        Vec3 eye = player.getEyePosition();
        nearbyProblems = data.uncovered().stream()
                .filter(problem -> eye.distanceToSqr(Vec3.atCenterOf(problem.pos())) < 32 * 32)
                .sorted(Comparator.comparingDouble(problem -> eye.distanceToSqr(Vec3.atCenterOf(problem.pos()))))
                .limit(12).toList();
    }

    private static void sample(IrrigationOverlayPayloads.SprinklerMark mark) {
        var level = Minecraft.getInstance().level;
        BlockPos sprinkler = mark.pos();
        if (level == null || !level.hasChunk(sprinkler.getX() >> 4, sprinkler.getZ() >> 4)) return;
        boolean hanging = CopperSprinklerBlock.isHanging(level.getBlockState(sprinkler));
        int below = hanging ? IrrigationCoverage.HANGING_BELOW : IrrigationCoverage.BELOW;
        int above = hanging ? IrrigationCoverage.HANGING_ABOVE : IrrigationCoverage.ABOVE;
        int range = Math.clamp(data.range(), 1, 4);
        for (int x = -range; x <= range; x++) for (int z = -range; z <= range; z++) {
            // Ground is below the covered crop position. Include all terraces and floors.
            for (int y = above - 1; y >= -below - 1; y--) {
                BlockPos ground = sprinkler.offset(x,y,z);
                if (!level.hasChunk(ground.getX() >> 4, ground.getZ() >> 4)) break;
                var state = level.getBlockState(ground);
                if (state.isAir() || state.getBlock() instanceof IrrigationConnectable || CropAdapters.get(state) != null) continue;
                var shape = state.getCollisionShape(level,ground);
                if (shape.isEmpty()) continue;
                var over = level.getBlockState(ground.above());
                if (!over.getCollisionShape(level,ground.above()).isEmpty() && CropAdapters.get(over) == null) continue;
                Surface surface = new Surface(ground, ground.getY()+shape.max(Direction.Axis.Y)+0.025, mark.state());
                building.merge(ground,surface,(a,b) -> priority(a.state()) >= priority(b.state()) ? a : b);
            }
        }
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!enabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var client = Minecraft.getInstance();
        if (client.level == null) return;
        double time = client.level.getGameTime() + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        var buffers = client.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x,-camera.y,-camera.z);
        float pulse = 0.7F + 0.15F*(float)Math.sin(time*0.055);
        for (Surface surface : surfaces.values()) {
            BlockPos pos = surface.pos();
            float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(pos)),MAX_RENDER_DISTANCE);
            if (fade <= 0) continue;
            int color = color(surface.state());
            double x=pos.getX(), y=surface.height()+1.05, z=pos.getZ();
            // Shared edges disappear: overlapping sprinklers read as one irrigated field.
            if (!joined(surface,pos.north())) FieldGuide.line(pose,lines,x,y,z,x+1,y,z,color,fade*pulse);
            if (!joined(surface,pos.south())) FieldGuide.line(pose,lines,x,y,z+1,x+1,y,z+1,color,fade*pulse);
            if (!joined(surface,pos.west())) FieldGuide.line(pose,lines,x,y,z,x,y,z+1,color,fade*pulse);
            if (!joined(surface,pos.east())) FieldGuide.line(pose,lines,x+1,y,z,x+1,y,z+1,color,fade*pulse);
            // Lift the thin guides above the foliage so mature crops do not hide the coverage.
            FieldGuide.line(pose,lines,x+.44,y,z+.5,x+.56,y,z+.5,color,fade*.4F);
            if (surface.state() == IrrigationVisual.ERROR)
                FieldGuide.line(pose,lines,x+.5,y,z+.44,x+.5,y,z+.56,color,fade*.6F);
        }
        for (var mark : data.sprinklers()) {
            BlockPos p = mark.pos();
            float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(p)),MAX_RENDER_DISTANCE);
            if (fade <= 0) continue;
            double phase = (time + Math.floorMod(p.asLong(),40)) % 40 / 40;
            FieldGuide.ring(pose,lines,p.getX()+.5,p.getY()+.45,p.getZ()+.5,
                    mark.state()==IrrigationVisual.ACTIVE ? .24+phase*.22 : .32,color(mark.state()),
                    fade*(mark.state()==IrrigationVisual.ACTIVE ? (float)(.85-phase*.6) : .65F));
        }
        for (var problem : nearbyProblems) {
            BlockPos p = problem.pos();
            float fade = FieldGuide.distanceFade(camera.distanceToSqr(Vec3.atCenterOf(p)),MAX_RENDER_DISTANCE);
            if (fade <= 0) continue;
            double y=p.getY()+1.06;
            int color=problem.type()==ProblemType.IRRIGATION_OFFLINE ? FieldGuide.FAULT : FieldGuide.GOLD;
            FieldGuide.line(pose,lines,p.getX()+.37,y,p.getZ()+.37,p.getX()+.63,y,p.getZ()+.63,color,fade*.65F);
            FieldGuide.line(pose,lines,p.getX()+.37,y,p.getZ()+.63,p.getX()+.63,y,p.getZ()+.37,color,fade*.65F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static boolean joined(Surface a, BlockPos neighbor) {
        Surface b=surfaces.get(neighbor);
        return b!=null && a.state()==b.state() && Math.abs(a.height()-b.height())<.01;
    }

    private static int color(IrrigationVisual state) {
        return switch (state) { case ACTIVE -> FieldGuide.WATER; case ERROR -> FieldGuide.FAULT; case OFF -> FieldGuide.IDLE; };
    }
}
