package fr.lkdm.homelink.farm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Checks the rendering defects that model-existence checks cannot detect. */
class ModelAssetsTest {
    private static final Path BLOCKS = Path.of(System.getProperty("homelink_farm.projectDir", "."))
            .resolve("src/main/resources/assets/homelink_farm/models/block");
    private static final List<String> TEMPLATES = List.of("farm_controller_template", "crop_monitor_template",
            "irrigation_pump_template", "template_sprinkler", "template_pipe_side", "template_pipe_up",
            "template_pipe_down", "template_pipe_core", "template_pipe_item");
    private static final List<String> DIRECTIONS = List.of("west", "east", "down", "up", "north", "south");

    @Test
    void everyTextureHasValidDimensionsAndAnimationFrames() throws Exception {
        Path textures = BLOCKS.getParent().getParent().resolve("textures");
        try (var files = Files.walk(textures)) {
            for (Path path : files.filter(p -> p.toString().endsWith(".png")).toList()) {
                var image = ImageIO.read(path.toFile());
                assertNotNull(image, "Unreadable PNG " + path);
                int width = image.getWidth(), height = image.getHeight();
                assertTrue(width > 0 && (width & (width - 1)) == 0, "Texture width must be a power of two: " + path);
                Path metadata = Path.of(path + ".mcmeta");
                if (Files.exists(metadata)) {
                    JsonObject animation = JsonParser.parseString(Files.readString(metadata)).getAsJsonObject().getAsJsonObject("animation");
                    assertNotNull(animation, "Missing animation declaration: " + path);
                    assertEquals(0, height % width, "Incomplete animation frame: " + path);
                    assertTrue(animation.get("frametime").getAsInt() > 0, "Invalid frame duration: " + path);
                } else {
                    assertEquals(width, height, "Unexpected non-square static texture: " + path);
                }
            }
        }
    }

    private static Map<String, String> inheritedTextures(Path path) throws Exception {
        JsonObject model = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        Map<String, String> textures = new HashMap<>();
        if (model.has("parent")) {
            String parent = model.get("parent").getAsString();
            if (parent.startsWith("homelink_farm:")) {
                textures.putAll(inheritedTextures(BLOCKS.getParent().resolve(parent.substring("homelink_farm:".length()) + ".json")));
            }
        }
        if (model.has("textures")) {
            model.getAsJsonObject("textures").entrySet().forEach(e -> textures.put(e.getKey(), e.getValue().getAsString()));
        }
        return textures;
    }

    @Test
    void concreteModelsResolveAllTextureAliases() throws Exception {
        try (var files = Files.walk(BLOCKS.getParent())) {
            for (Path path : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                if (path.getFileName().toString().contains("template")) continue;
                Map<String, String> textures = inheritedTextures(path);
                for (String value : textures.values()) {
                    var visited = new HashSet<String>();
                    while (value.startsWith("#")) {
                        assertTrue(visited.add(value), "Cyclic texture alias in " + path);
                        value = textures.get(value.substring(1));
                        assertNotNull(value, "Unresolved texture alias in " + path);
                    }
                    if (value.startsWith("homelink_farm:")) {
                        Path png = BLOCKS.getParent().getParent().resolve("textures")
                                .resolve(value.substring("homelink_farm:".length()) + ".png");
                        assertTrue(Files.isRegularFile(png), "Missing texture " + png + " in " + path);
                    }
                }
            }
        }
    }

    private static JsonArray elements(String name) throws Exception {
        return JsonParser.parseString(Files.readString(BLOCKS.resolve(name + ".json")))
                .getAsJsonObject().getAsJsonArray("elements");
    }

    private static double coordinate(JsonObject box, String edge, int axis) {
        return box.getAsJsonArray(edge).get(axis).getAsDouble();
    }

    private static boolean solid(JsonObject box) {
        if (box.has("neoforge_data")) return false;
        for (int axis = 0; axis < 3; axis++) {
            if (coordinate(box, "to", axis) <= coordinate(box, "from", axis)) return false;
        }
        return true;
    }

    @Test
    void opaqueFacesNeverFightOnTheSamePlane() throws Exception {
        for (String name : TEMPLATES) {
            JsonArray boxes = elements(name);
            for (int i = 0; i < boxes.size(); i++) {
                JsonObject a = boxes.get(i).getAsJsonObject();
                if (!solid(a)) continue;
                for (int j = i + 1; j < boxes.size(); j++) {
                    JsonObject b = boxes.get(j).getAsJsonObject();
                    if (!solid(b)) continue;
                    for (int face = 0; face < 6; face++) {
                        String direction = DIRECTIONS.get(face);
                        if (!a.getAsJsonObject("faces").has(direction) || !b.getAsJsonObject("faces").has(direction)) continue;
                        int axis = face / 2;
                        String edge = face % 2 == 0 ? "from" : "to";
                        if (Math.abs(coordinate(a, edge, axis) - coordinate(b, edge, axis)) > 0.00001) continue;
                        boolean overlaps = true;
                        for (int other = 0; other < 3; other++) {
                            if (other == axis) continue;
                            double intersection = Math.min(coordinate(a, "to", other), coordinate(b, "to", other))
                                    - Math.max(coordinate(a, "from", other), coordinate(b, "from", other));
                            if (intersection <= 0.00001) overlaps = false;
                        }
                        assertFalse(overlaps, name + " elements " + i + "/" + j + " have overlapping " + direction + " faces");
                    }
                }
            }
        }
    }

    @Test
    void sharedMaterialsKeepOneTexelPerModelPixel() throws Exception {
        for (String name : TEMPLATES) {
            for (var element : elements(name)) {
                JsonObject box = element.getAsJsonObject();
                for (var entry : box.getAsJsonObject("faces").entrySet()) {
                    JsonObject face = entry.getValue().getAsJsonObject();
                    if (!face.get("texture").getAsString().equals("#materials")) continue;
                    String side = entry.getKey();
                    int horizontal = side.equals("east") || side.equals("west") ? 2 : 0;
                    int vertical = side.equals("up") || side.equals("down") ? 2 : 1;
                    JsonArray uv = face.getAsJsonArray("uv");
                    assertNotNull(uv, name + " material face needs explicit atlas UVs");
                    assertEquals(coordinate(box, "to", horizontal) - coordinate(box, "from", horizontal),
                            4 * Math.abs(uv.get(2).getAsDouble() - uv.get(0).getAsDouble()), 0.00001, name + " horizontal density");
                    assertEquals(coordinate(box, "to", vertical) - coordinate(box, "from", vertical),
                            4 * Math.abs(uv.get(3).getAsDouble() - uv.get(1).getAsDouble()), 0.00001, name + " vertical density");
                    for (var value : uv) assertTrue(value.getAsDouble() >= 0 && value.getAsDouble() <= 16, name + " atlas UV outside texture");
                    assertEquals((int) (uv.get(0).getAsDouble() / 8), (int) (uv.get(2).getAsDouble() / 8), name + " crosses material column");
                    assertEquals((int) (uv.get(1).getAsDouble() / 8), (int) (uv.get(3).getAsDouble() / 8), name + " crosses material row");
                }
            }
        }
    }
}
