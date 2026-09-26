package fr.lkdm.homelink.farm.gametest.client;

import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.insertNext;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.onServer;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.pause;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.screenshot;
import static fr.lkdm.homelink.farm.gametest.client.ClientSmoke.step;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Interactive smoke session ({@code ./gradlew runClientSmoke -PsmokeScenario=live}): the client stays
 * open and plays the command batches written to {@code build/client-smoke/live/commands.txt}, so
 * models, textures and scenes can be checked many times without restarting the game.
 * After each batch it writes the batch number to {@code live/ack.txt} ({@code "<n> failed: ..."} on error;
 * a batch with an unknown command is rejected before anything runs). Send batches with
 * {@code scripts/live-client.sh}.
 * <p>
 * One command per line ({@code #} starts a comment):
 * <ul>
 *   <li>{@code reload} reloads the resource packs (copy edited assets to {@code build/resources/main} first)</li>
 *   <li>{@code cmd <command>} runs a server command as the player, e.g. {@code cmd give @s homelink_farm:farm_connector}</li>
 *   <li>{@code hold <0-8>} selects a hotbar slot; {@code camera first|front|back}</li>
 *   <li>{@code inventory} / {@code close} opens the inventory / closes the current screen</li>
 *   <li>{@code wait <ticks>}, {@code screenshot <name>}</li>
 *   <li>{@code scene field|farmbot|textures|help|overlays|assets} plays an existing smoke scenario</li>
 *   <li>{@code quit} ends the session</li>
 * </ul>
 */
final class LiveSession {
    private static int batch;
    private static List<String> pending;
    private static CompletableFuture<Void> reloading;

    private LiveSession() {
    }

    private static Path folder() {
        return ClientSmoke.gameDirectory().toPath().resolve("live");
    }

    /** Waits for the next command batch, then plays it followed by a new wait. */
    static void define() {
        step("live: wait for commands", LiveSession::poll, () -> {
            List<String> commands = pending;
            pending = null;
            HomeLinkFarm.LOGGER.info("HOMELINK_FARM_LIVE batch {}: {} commands", batch, commands.size());
            insertNext(() -> play(commands));
        });
    }

    private static boolean poll() {
        Path file = folder().resolve("commands.txt");
        if (!Files.exists(file)) return false;
        try {
            pending = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                    .map(String::strip).filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
            Files.delete(file);
        } catch (IOException ignored) {
            return false; // Still being written; retry next tick.
        }
        batch++;
        return true;
    }

    private static void play(List<String> commands) {
        for (String line : commands) {
            String[] parts = line.split("\\s+", 2);
            String arg = parts.length > 1 ? parts[1] : "";
            switch (parts[0]) {
                case "reload" -> {
                    step("live: reload", () -> true, () -> reloading = Minecraft.getInstance().reloadResourcePacks());
                    // The loading overlay fades out after the reload itself completes.
                    step("live: reload done", () -> reloading.isDone() && Minecraft.getInstance().getOverlay() == null, () -> { });
                    pause(5);
                }
                case "cmd" -> {
                    String command = arg.startsWith("/") ? arg.substring(1) : arg;
                    step("live: cmd " + command, () -> true, () -> onServer(player -> player.server.getCommands()
                            .performPrefixedCommand(player.createCommandSourceStack().withPermission(4), command)));
                    pause(2);
                }
                case "hold" -> step("live: hold", () -> true, () -> Minecraft.getInstance().player.getInventory().selected = Integer.parseInt(arg));
                case "camera" -> step("live: camera", () -> true, () -> Minecraft.getInstance().options.setCameraType(switch (arg) {
                    case "front" -> CameraType.THIRD_PERSON_FRONT;
                    case "back" -> CameraType.THIRD_PERSON_BACK;
                    default -> CameraType.FIRST_PERSON;
                }));
                case "inventory" -> step("live: inventory", () -> true,
                        () -> Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player)));
                case "close" -> step("live: close", () -> true, () -> Minecraft.getInstance().setScreen(null));
                case "wait" -> pause(Integer.parseInt(arg));
                case "screenshot" -> step("live: screenshot", () -> true, () -> screenshot(arg));
                case "scene" -> scene(arg);
                case "quit" -> {
                    step("live: quit", () -> true, () -> ack(""));
                    return;
                }
                default -> throw new IllegalArgumentException("Unknown live command: " + line);
            }
        }
        step("live: batch done", () -> true, () -> ack(""));
        define();
    }

    private static void scene(String name) {
        switch (name) {
            case "field" -> SmokeScenario.define();
            case "farmbot" -> FarmBotSmoke.define();
            case "textures" -> TextureSmoke.define();
            case "help" -> HelpSmoke.define();
            case "overlays" -> OverlaySmoke.define();
            case "assets" -> AssetSmoke.define();
            default -> throw new IllegalArgumentException("Unknown scene: " + name);
        }
    }

    /** Called by {@link ClientSmoke} when a step fails: reports it and waits for the next batch. */
    static void failed(Throwable failure) {
        ack(" failed: " + failure);
        define();
    }

    private static void ack(String suffix) {
        try {
            Files.createDirectories(folder());
            Files.writeString(folder().resolve("ack.txt"), batch + suffix, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            HomeLinkFarm.LOGGER.error("HOMELINK_FARM_LIVE cannot write ack", exception);
        }
        HomeLinkFarm.LOGGER.info("HOMELINK_FARM_LIVE batch {} done{}", batch, suffix);
    }
}
