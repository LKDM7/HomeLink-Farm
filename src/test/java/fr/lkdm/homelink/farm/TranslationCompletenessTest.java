package fr.lkdm.homelink.farm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lkdm.homelink.farm.farm.controller.LinkResult;
import fr.lkdm.homelink.farm.farm.crop.ComparatorMode;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemType;
import fr.lkdm.homelink.farm.farm.irrigation.PumpStatus;
import fr.lkdm.homelink.farm.farm.irrigation.RedstoneMode;
import fr.lkdm.homelink.farm.homelink.FarmIds;
import fr.lkdm.homelink.farm.homelink.HomeNetworkBinding;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

/** Every text the player can see exists in English AND French. */
class TranslationCompletenessTest {
    private static final Path PROJECT = Path.of(System.getProperty("homelink_farm.projectDir", "."));
    private static final Path LANG = PROJECT.resolve("src/main/resources/assets/homelink_farm/lang");
    private static final Pattern LITERAL_KEY = Pattern.compile("\"((?:gui|message|tooltip|problem|pump_status|comparator_mode|redstone_mode|"
            + "action|status|metric|block|item|key|itemGroup)\\.homelink_farm[a-z0-9_.]*|key\\.categories\\.homelink_farm)\"");

    private static JsonObject load(String locale) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(locale + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void englishAndFrenchHaveTheSameNonEmptyKeys() throws IOException {
        JsonObject en = load("en_us");
        JsonObject fr = load("fr_fr");
        assertEquals(new TreeSet<>(en.keySet()), new TreeSet<>(fr.keySet()));
        for (String key : fr.keySet()) assertTrue(!fr.get(key).getAsString().isBlank(), "empty French text for " + key);
    }

    @Test
    void everyKeyBuiltByTheCodeIsTranslated() throws IOException, IllegalAccessException {
        JsonObject fr = load("fr_fr");
        List<String> expected = new ArrayList<>();
        for (ProblemType type : ProblemType.values()) expected.add("problem.homelink_farm." + lower(type));
        for (PumpStatus status : PumpStatus.values()) expected.add("pump_status.homelink_farm." + lower(status));
        for (ComparatorMode mode : ComparatorMode.values()) expected.add("comparator_mode.homelink_farm." + lower(mode));
        for (RedstoneMode mode : RedstoneMode.values()) expected.add("redstone_mode.homelink_farm." + lower(mode));
        for (LinkResult result : LinkResult.values()) expected.add(result.translationKey());
        for (HomeNetworkBinding.Result result : HomeNetworkBinding.Result.values()) expected.add("message.homelink_farm.network." + lower(result));
        for (String zone : List.of("set", "too_large", "too_far", "corner_a", "corner_b", "incomplete")) expected.add("message.homelink_farm.zone." + zone);
        for (String block : List.of("farm_controller", "crop_monitor", "irrigation_pump", "copper_sprinkler", "copper_pipe")) {
            expected.add("tooltip.homelink_farm." + block + ".1");
            expected.add("tooltip.homelink_farm." + block + ".2");
        }
        for (String prefix : List.of("", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_")) {
            expected.add("block.homelink_farm." + prefix + "copper_pipe");
        }
        for (Field field : FarmIds.class.getFields()) {
            if (field.getType() != ResourceLocation.class) continue;
            String name = field.getName();
            if (name.equals("FARM_CONTROLLER") || name.equals("IRRIGATION_PUMP") || name.startsWith("ACTION_") || Set.of(
                    "CROP_READY", "PROBLEM_DETECTED", "IRRIGATION_FAILURE", "IRRIGATION_RESTORED", "PUMP_OVER_CAPACITY").contains(name)) continue;
            expected.add("metric.homelink_farm." + ((ResourceLocation) field.get(null)).getPath());
        }
        for (String value : List.of("maxLinkDistance", "maxComponentsPerController", "maxCropMonitorVolume", "maxZoneDistance",
                "cropScanInterval", "cropScanBudgetPerTick", "globalScanBudgetPerTick", "locateDuration", "maxSprinklersPerPump",
                "maxNetworkNodes", "sprinklerRange", "irrigationGrowthBonus", "controller", "cropMonitor", "irrigation")) {
            expected.add("homelink_farm.configuration." + value);
            expected.add("homelink_farm.configuration." + value + ".tooltip");
        }
        expected.add("fml.menu.mods.info.description.homelink_farm");
        Set<String> literal = literalKeysInSources();
        assertTrue(literal.size() > 60, "source scan found only " + literal.size() + " keys");
        expected.addAll(literal);
        List<String> missing = expected.stream().distinct().filter(key -> !fr.has(key)).toList();
        assertTrue(missing.isEmpty(), "Missing translations: " + missing);
    }

    /** Keys written literally in the Java sources (translatable(...), withFallback(...), label keys...). */
    private static Set<String> literalKeysInSources() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(PROJECT.resolve("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = LITERAL_KEY.matcher(Files.readString(file, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    String key = matcher.group(1);
                    // Prefixes completed at runtime ("...status." + name) are covered by the enum checks above.
                    if (!key.endsWith(".") && !key.endsWith("homelink_farm")) keys.add(key);
                }
            }
        }
        return keys;
    }

    private static String lower(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
