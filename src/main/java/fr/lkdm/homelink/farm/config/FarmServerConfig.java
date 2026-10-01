package fr.lkdm.homelink.farm.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-side configuration (per world, synchronized to clients by NeoForge). */
public final class FarmServerConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue MAX_LINK_DISTANCE;
    public static final ModConfigSpec.IntValue MAX_COMPONENTS_PER_CONTROLLER;

    public static final ModConfigSpec.IntValue MAX_CROP_MONITOR_VOLUME;
    public static final ModConfigSpec.IntValue MAX_ZONE_DISTANCE;
    public static final ModConfigSpec.IntValue CROP_SCAN_INTERVAL;
    public static final ModConfigSpec.IntValue CROP_SCAN_BUDGET_PER_TICK;
    public static final ModConfigSpec.IntValue GLOBAL_SCAN_BUDGET_PER_TICK;
    public static final ModConfigSpec.IntValue LOCATE_DURATION;

    public static final ModConfigSpec.IntValue MAX_SPRINKLERS_PER_PUMP;
    public static final ModConfigSpec.IntValue MAX_NETWORK_NODES;
    public static final ModConfigSpec.IntValue SPRINKLER_RANGE;
    public static final ModConfigSpec.DoubleValue IRRIGATION_GROWTH_BONUS;
    public static final ModConfigSpec.IntValue PIPE_OXIDATION_DAYS;

    public static final ModConfigSpec.IntValue FARMBOT_BATTERY_CAPACITY;
    public static final ModConfigSpec.IntValue FARMBOT_LOW_BATTERY_THRESHOLD;
    public static final ModConfigSpec.DoubleValue FARMBOT_MOVEMENT_CONSUMPTION;
    public static final ModConfigSpec.DoubleValue FARMBOT_HARVEST_CONSUMPTION;
    public static final ModConfigSpec.DoubleValue FARMBOT_IDLE_CONSUMPTION;
    public static final ModConfigSpec.IntValue FARMBOT_RECHARGE_TIME;
    public static final ModConfigSpec.IntValue FARMBOT_TARGET_RETRY_LIMIT;
    public static final ModConfigSpec.IntValue FARMBOT_SEARCH_COOLDOWN;

    public static final ModConfigSpec.IntValue PUMP_ENERGY;
    public static final ModConfigSpec.IntValue CROP_MONITOR_ENERGY;
    public static final ModConfigSpec.IntValue CONTROLLER_ENERGY;
    public static final ModConfigSpec.IntValue FARMBOT_STATION_ENERGY;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("controller");
        MAX_LINK_DISTANCE = builder
                .comment("Maximum distance, in blocks, between a Farm Controller and a linked component.")
                .defineInRange("maxLinkDistance", 64, 8, 256);
        MAX_COMPONENTS_PER_CONTROLLER = builder
                .comment("Maximum number of components (Crop Monitors, Pumps...) linked to one Farm Controller.")
                .defineInRange("maxComponentsPerController", 32, 1, 256);
        builder.pop();

        builder.push("cropMonitor");
        MAX_CROP_MONITOR_VOLUME = builder
                .comment("Maximum number of blocks (X * Y * Z) in a zone drawn with the Farm Connector.",
                        "The default whole-chunk zone of a Crop Monitor is built by the server and not limited.")
                .defineInRange("maxCropMonitorVolume", 32768, 64, 262144);
        MAX_ZONE_DISTANCE = builder
                .comment("Maximum horizontal/vertical distance between a Crop Monitor and any block of its zone.")
                .defineInRange("maxZoneDistance", 48, 4, 128);
        CROP_SCAN_INTERVAL = builder
                .comment("Ticks between the end of a zone scan and the start of the next one (20 ticks = 1 second).")
                .defineInRange("cropScanInterval", 100, 20, 12000);
        CROP_SCAN_BUDGET_PER_TICK = builder
                .comment("Maximum zone positions one Crop Monitor examines per tick.")
                .defineInRange("cropScanBudgetPerTick", 512, 16, 16384);
        GLOBAL_SCAN_BUDGET_PER_TICK = builder
                .comment("Maximum zone positions examined per tick by all Crop Monitors of the server together.")
                .defineInRange("globalScanBudgetPerTick", 8192, 64, 131072);
        LOCATE_DURATION = builder
                .comment("How long (ticks) a LOCATE marker stays visible to the player who requested it.")
                .defineInRange("locateDuration", 400, 20, 6000);
        builder.pop();

        builder.push("irrigation");
        MAX_SPRINKLERS_PER_PUMP = builder
                .comment("Sprinklers one Irrigation Pump can feed. Official rule: 5. Above pumps x this value,",
                        "the whole network is OVER_CAPACITY and no sprinkler irrigates.")
                .defineInRange("maxSprinklersPerPump", 5, 1, 64);
        MAX_NETWORK_NODES = builder
                .comment("Maximum pipes + pumps + sprinklers in one network (larger networks report NETWORK_TOO_LARGE).")
                .defineInRange("maxNetworkNodes", 1024, 16, 16384);
        SPRINKLER_RANGE = builder
                .comment("Horizontal reach of a Copper Sprinkler: it covers a (2 * range + 1) square. Default 2 = 5 x 5.",
                        "Vertically it reaches 12 blocks below; up to 1 block above when standing, or its own level when hanging.")
                .defineInRange("sprinklerRange", 2, 1, 4);
        IRRIGATION_GROWTH_BONUS = builder
                .comment("Growth speed bonus of irrigated crops (0.33 = +33%). Never stacks between sprinklers.",
                        "Implemented as extra vanilla random ticks; the randomTickSpeed game rule is never modified.")
                .defineInRange("irrigationGrowthBonus", 0.33, 0.0, 1.0);
        PIPE_OXIDATION_DAYS = builder
                .comment("In-game days (in loaded chunks, at the default randomTickSpeed) for an unwaxed copper pipe",
                        "to go from new to fully oxidized. Oxidation is cosmetic: every stage carries water.")
                .defineInRange("pipeOxidationDays", 100, 1, 10000);
        builder.pop();

        builder.push("farmbot");
        FARMBOT_BATTERY_CAPACITY = builder
                .comment("Energy units stored by a full FarmBot battery (an internal HomeLink Farm mechanic, not FE).")
                .defineInRange("farmbotBatteryCapacity", 1000, 100, 100000);
        FARMBOT_LOW_BATTERY_THRESHOLD = builder
                .comment("Battery percentage at or below which a FarmBot drops its task and drives back to its station.")
                .defineInRange("farmbotLowBatteryThreshold", 20, 5, 90);
        FARMBOT_MOVEMENT_CONSUMPTION = builder
                .comment("Energy used per block driven.")
                .defineInRange("farmbotMovementConsumption", 1.0, 0.0, 100.0);
        FARMBOT_HARVEST_CONSUMPTION = builder
                .comment("Energy used per harvested crop.")
                .defineInRange("farmbotHarvestConsumption", 3.0, 0.0, 1000.0);
        FARMBOT_IDLE_CONSUMPTION = builder
                .comment("Energy used per minute while waiting away from the station (0 = none).")
                .defineInRange("farmbotIdleConsumption", 0.0, 0.0, 1000.0);
        FARMBOT_RECHARGE_TIME = builder
                .comment("Seconds a docked FarmBot needs to charge from 0 to 100%.")
                .defineInRange("farmbotRechargeTime", 25, 1, 3600);
        FARMBOT_TARGET_RETRY_LIMIT = builder
                .comment("Failed attempts to reach a crop before it is ignored for a while.")
                .defineInRange("farmbotTargetRetryLimit", 3, 1, 20);
        FARMBOT_SEARCH_COOLDOWN = builder
                .comment("Ticks between two searches for a mature crop when none was available.")
                .defineInRange("farmbotSearchCooldown", 40, 5, 1200);
        builder.pop();

        builder.comment("HomeLink Energy (HE) each machine uses per minute (1200 ticks) while it runs.",
                "Without enough HE a machine stops until power comes back. For scale: a Solar Panel I",
                "averages 100 HE per minute over a day, a Solar Panel III 1000. 0 lets the machine run for free.").push("energy");
        PUMP_ENERGY = builder
                .comment("Irrigation Pump, while it pumps water to its sprinklers.")
                .defineInRange("pumpEnergyPerMinute", 60, 0, 1_000_000);
        CROP_MONITOR_ENERGY = builder
                .comment("Crop Monitor, while it scans its zone.")
                .defineInRange("cropMonitorEnergyPerMinute", 20, 0, 1_000_000);
        CONTROLLER_ENERGY = builder
                .comment("Farm Controller, while it aggregates its farm.")
                .defineInRange("controllerEnergyPerMinute", 30, 0, 1_000_000);
        FARMBOT_STATION_ENERGY = builder
                .comment("FarmBot Station, while a FarmBot is assigned to it; the robot only recharges and leaves from a powered station.")
                .defineInRange("farmbotStationEnergyPerMinute", 120, 0, 1_000_000);
        builder.pop();

        SPEC = builder.build();
    }

    private FarmServerConfig() {
    }
}
