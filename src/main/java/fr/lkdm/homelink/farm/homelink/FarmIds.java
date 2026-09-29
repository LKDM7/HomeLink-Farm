package fr.lkdm.homelink.farm.homelink;

import fr.lkdm.homecore.api.action.StandardActions;
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
    public static final ResourceLocation FARMBOT_STATION = HomeLinkFarm.id("farmbot_station");

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

    // FarmBot Station metrics
    public static final ResourceLocation FARMBOT_INSTALLED = HomeLinkFarm.id("farmbot_installed");
    public static final ResourceLocation FARMBOT_STATUS = HomeLinkFarm.id("farmbot_status");
    public static final ResourceLocation FARMBOT_BATTERY = HomeLinkFarm.id("farmbot_battery");
    public static final ResourceLocation FARMBOT_STORAGE = HomeLinkFarm.id("farmbot_storage");
    public static final ResourceLocation FARMBOT_HARVESTED = HomeLinkFarm.id("farmbot_harvested");
    public static final ResourceLocation FARMBOT_CURRENT_TARGET = HomeLinkFarm.id("farmbot_current_target");
    public static final ResourceLocation STATION_OUTPUT_USAGE = HomeLinkFarm.id("station_output_usage");

    // Actions
    /** Pump on/off: HomeCore's standard power toggle. */
    public static final ResourceLocation ACTION_ENABLED = StandardActions.POWER;
    public static final ResourceLocation ACTION_RESCAN = HomeLinkFarm.id("rescan");
    public static final ResourceLocation ACTION_START = HomeLinkFarm.id("start");
    public static final ResourceLocation ACTION_PAUSE = HomeLinkFarm.id("pause");
    public static final ResourceLocation ACTION_RETURN_HOME = HomeLinkFarm.id("return_home");

    // Events (emitted on state transitions only)
    public static final ResourceLocation CROP_READY = HomeLinkFarm.id("crop_ready");
    public static final ResourceLocation PROBLEM_DETECTED = HomeLinkFarm.id("problem_detected");
    public static final ResourceLocation IRRIGATION_FAILURE = HomeLinkFarm.id("irrigation_failure");
    public static final ResourceLocation IRRIGATION_RESTORED = HomeLinkFarm.id("irrigation_restored");
    public static final ResourceLocation PUMP_OVER_CAPACITY = HomeLinkFarm.id("pump_over_capacity");

    public static final ResourceLocation FARMBOT_LOW_BATTERY = HomeLinkFarm.id("farmbot_low_battery");
    public static final ResourceLocation FARMBOT_STORAGE_FULL = HomeLinkFarm.id("farmbot_storage_full");
    public static final ResourceLocation FARMBOT_STUCK = HomeLinkFarm.id("farmbot_stuck");
    public static final ResourceLocation FARMBOT_OUTPUT_BLOCKED = HomeLinkFarm.id("farmbot_output_blocked");
    public static final ResourceLocation FARMBOT_RETURNED = HomeLinkFarm.id("farmbot_returned");
    public static final ResourceLocation FARMBOT_HARVEST_COMPLETE = HomeLinkFarm.id("farmbot_harvest_complete");

    public static final Set<ResourceLocation> FARMBOT_EVENTS = Set.of(FARMBOT_LOW_BATTERY, FARMBOT_STORAGE_FULL, FARMBOT_STUCK,
            FARMBOT_OUTPUT_BLOCKED, FARMBOT_RETURNED, FARMBOT_HARVEST_COMPLETE);
    public static final Set<ResourceLocation> CONTROLLER_EVENTS = Set.of(CROP_READY, PROBLEM_DETECTED, IRRIGATION_FAILURE, IRRIGATION_RESTORED);
    public static final Set<ResourceLocation> PUMP_EVENTS = Set.of(PUMP_OVER_CAPACITY, IRRIGATION_FAILURE, IRRIGATION_RESTORED);

    private FarmIds() {
    }
}
