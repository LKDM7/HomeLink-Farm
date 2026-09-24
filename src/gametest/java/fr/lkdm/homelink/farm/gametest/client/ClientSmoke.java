package fr.lkdm.homelink.farm.gametest.client;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import fr.lkdm.homelink.farm.gametest.HomeLinkFarmGameTestMod;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Development-only, opt-in ({@code -Dhomelink_farm.clientSmoke=true}) real client run:
 * creates a flat creative world, builds a farm through the integrated server, drives the
 * device screens and client features, saves screenshots and quits. Never in the release JAR.
 */
@EventBusSubscriber(modid = HomeLinkFarmGameTestMod.MOD_ID, value = Dist.CLIENT)
public final class ClientSmoke {
    private static final String SCENARIO = System.getProperty("homelink_farm.clientSmoke", "");
    private static final boolean ENABLED = !SCENARIO.isEmpty() && !SCENARIO.equals("false");
    private static final boolean PLAYER = SCENARIO.equals("player");
    private static final long TIMEOUT_NANOS = (PLAYER ? 420L : 90L) * 1_000_000_000L;
    /** Folder of the world created for this run (used to reload it). */
    static String levelId;

    private record Step(String name, BooleanSupplier condition, Runnable action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static int stage = -2;
    private static int stepIndex;
    private static int waitTicks;
    private static long started;
    private static volatile Throwable serverFailure;

    private ClientSmoke() {
    }

    /** Waits until {@code condition} holds (checked every client tick), then runs {@code action}. */
    static void step(String name, BooleanSupplier condition, Runnable action) {
        STEPS.add(new Step(name, condition, action));
    }

    static void pause(int ticks) {
        STEPS.add(new Step("pause", () -> true, () -> waitTicks = ticks));
    }

    /** Runs server-side work on the integrated server thread, recording failures. */
    static void onServer(Consumer<ServerPlayer> work) {
        Minecraft client = Minecraft.getInstance();
        MinecraftServer server = client.getSingleplayerServer();
        var id = client.player.getUUID();
        server.execute(() -> {
            try {
                work.accept(server.getPlayerList().getPlayer(id));
            } catch (Throwable failure) {
                serverFailure = failure;
            }
        });
    }

    static void screenshot(String name) {
        Minecraft client = Minecraft.getInstance();
        Screenshot.grab(client.gameDirectory, "smoke_" + name + ".png", client.getMainRenderTarget(),
                message -> HomeLinkFarm.LOGGER.info("HOMELINK_FARM_SMOKE screenshot {} -> {}", name, message.getString()));
    }

    /** Presses the first button of the current screen whose label starts with {@code prefix}, like a player click. */
    static void press(String prefix) {
        var screen = Minecraft.getInstance().screen;
        check(screen != null, "No screen open to press '" + prefix + "'");
        var button = screen.children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(candidate -> candidate.getMessage().getString().startsWith(prefix) && candidate.active)
                .findFirst().orElseThrow(() -> new IllegalStateException("No active button '" + prefix + "' on " + screen));
        button.onPress();
    }

    /** Presses the button whose label is the given translation key (language independent). */
    static void pressKey(String translationKey) {
        var screen = Minecraft.getInstance().screen;
        check(screen != null, "No screen open to press " + translationKey);
        var button = screen.children().stream()
                .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                .map(net.minecraft.client.gui.components.Button.class::cast)
                .filter(candidate -> candidate.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                        && contents.getKey().equals(translationKey))
                .findFirst().orElseThrow(() -> new IllegalStateException("No button " + translationKey + " on " + screen));
        button.onPress();
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || stage == Integer.MAX_VALUE) return;
        Minecraft client = Minecraft.getInstance();
        try {
            if (stage == -2) {
                if (client.screen instanceof AccessibilityOnboardingScreen onboarding) {
                    onboarding.onClose();
                    return;
                }
                if (!(client.screen instanceof TitleScreen)) return;
                stage = -1;
                started = System.nanoTime();
                createWorld(client);
                return;
            }
            if (System.nanoTime() - started > TIMEOUT_NANOS) throw new IllegalStateException("Timed out at step " + stepIndex);
            if (serverFailure != null) throw new IllegalStateException("Server-side step failed", serverFailure);
            if (stage == -1) {
                if (client.player == null || client.getSingleplayerServer() == null || client.level == null) return;
                stage = 0;
                if (SCENARIO.equals("textures")) {
                    TextureSmoke.define();
                } else if (SCENARIO.equals("help")) {
                    HelpSmoke.define();
                } else if (SCENARIO.equals("overlays")) {
                    OverlaySmoke.define();
                } else if (PLAYER) {
                    PlayerSmoke.define();
                } else {
                    AssetSmoke.define();
                    SmokeScenario.define();
                }
                HomeLinkFarm.LOGGER.info("HOMELINK_FARM_SMOKE world ready, {} steps", STEPS.size());
                return;
            }
            if (waitTicks > 0) {
                waitTicks--;
                return;
            }
            if (stepIndex >= STEPS.size()) {
                HomeLinkFarm.LOGGER.info("HOMELINK_FARM_CLIENT_SMOKE_OK steps={}", STEPS.size());
                shutdown(client);
                return;
            }
            Step current = STEPS.get(stepIndex);
            if (!current.condition().getAsBoolean()) return;
            current.action().run();
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_SMOKE step {} '{}' done", stepIndex, current.name());
            stepIndex++;
        } catch (Throwable failure) {
            HomeLinkFarm.LOGGER.error("HOMELINK_FARM_CLIENT_SMOKE_FAILED at step {}", stepIndex, failure);
            shutdown(client);
        }
    }

    private static void createWorld(Minecraft client) {
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        var settings = new LevelSettings("HomeLink Farm Smoke", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        levelId = "homelink-farm-smoke-" + System.currentTimeMillis();
        client.createWorldOpenFlows().createFreshLevel(levelId, settings,
                new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), new TitleScreen());
    }

    private static void shutdown(Minecraft client) {
        stage = Integer.MAX_VALUE;
        if (client.level != null) client.level.disconnect();
        client.disconnect();
        HomeLinkFarm.LOGGER.info("HOMELINK_FARM_CLIENT_SMOKE_SHUTDOWN");
        client.stop();
    }

    static File gameDirectory() {
        return Minecraft.getInstance().gameDirectory;
    }
}
