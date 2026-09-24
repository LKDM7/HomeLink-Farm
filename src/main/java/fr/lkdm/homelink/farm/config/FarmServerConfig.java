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
                .comment("Maximum number of blocks (X * Y * Z) in one Crop Monitor zone.")
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
                .comment("Growth speed bonus of irrigated crops (0.20 = +20%). Never stacks between sprinklers.",
                        "Implemented as extra vanilla random ticks; the randomTickSpeed game rule is never modified.")
                .defineInRange("irrigationGrowthBonus", 0.20, 0.0, 1.0);
        builder.pop();

        SPEC = builder.build();
    }

    private FarmServerConfig() {
    }
}
