package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homelink.farm.HomeLinkFarm;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * Stable identifiers published to HomeCore. Consumers (HomeLink Dashboard, HomeLink Tasks,
 * Holographique Map...) only depend on these ids through HomeCore, never on this mod.
 */
public final class FarmIds {
    // Device types
    public static final ResourceLocation FARM_CONTROLLER = HomeLinkFarm.id("farm_controller");
    public static final ResourceLocation IRRIGATION_PUMP = HomeLinkFarm.id("irrigation_pump");

    // Farm Controller metrics
    public static final ResourceLocation CROP_COUNT = HomeLinkFarm.id("crop_count");
    public static final ResourceLocation READY_PERCENTAGE = HomeLinkFarm.id("ready_percentage");
    public static final ResourceLocation MATURITY = HomeLinkFarm.id("maturity");
    public static final ResourceLocation IRRIGATION_COVERAGE = HomeLinkFarm.id("irrigation_coverage");
    public static final ResourceLocation IRRIGATED_CROPS = HomeLinkFarm.id("irrigated_crops");
    public static final ResourceLocation PROBLEM_COUNT = HomeLinkFarm.id("problem_count");
    public static final ResourceLocation CROP_AREAS = HomeLinkFarm.id("crop_areas");
    public static final ResourceLocation PUMPS = HomeLinkFarm.id("pumps");
    public static final ResourceLocation GROWTH_BONUS = HomeLinkFarm.id("growth_bonus");

    // Irrigation Pump metrics (irrigated_crops is shared with the controller)
    public static final ResourceLocation PUMP_STATUS = HomeLinkFarm.id("pump_status");
    public static final ResourceLocation ENABLED = HomeLinkFarm.id("enabled");
    public static final ResourceLocation WATER_AVAILABLE = HomeLinkFarm.id("water_available");
    public static final ResourceLocation SPRINKLERS_CONNECTED = HomeLinkFarm.id("sprinklers_connected");
    public static final ResourceLocation SPRINKLER_CAPACITY = HomeLinkFarm.id("sprinkler_capacity");
    public static final ResourceLocation OVER_CAPACITY = HomeLinkFarm.id("over_capacity");

    // Actions
    public static final ResourceLocation ACTION_ENABLED = ENABLED;
    public static final ResourceLocation ACTION_RESCAN = HomeLinkFarm.id("rescan");

    // Events (emitted on state transitions only)
    public static final ResourceLocation CROP_READY = HomeLinkFarm.id("crop_ready");
    public static final ResourceLocation PROBLEM_DETECTED = HomeLinkFarm.id("problem_detected");
    public static final ResourceLocation IRRIGATION_FAILURE = HomeLinkFarm.id("irrigation_failure");
    public static final ResourceLocation IRRIGATION_RESTORED = HomeLinkFarm.id("irrigation_restored");
    public static final ResourceLocation PUMP_OVER_CAPACITY = HomeLinkFarm.id("pump_over_capacity");

    public static final Set<ResourceLocation> CONTROLLER_EVENTS = Set.of(CROP_READY, PROBLEM_DETECTED, IRRIGATION_FAILURE, IRRIGATION_RESTORED);
    public static final Set<ResourceLocation> PUMP_EVENTS = Set.of(PUMP_OVER_CAPACITY, IRRIGATION_FAILURE, IRRIGATION_RESTORED);

    private FarmIds() {
    }
}
