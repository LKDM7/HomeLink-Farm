package fr.lkdm.homelink.farm.gametest;

import fr.lkdm.homelink.farm.config.FarmServerConfig;
import java.util.List;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The GameTests written before machines needed HomeLink Energy build farms without any power
 * source. They run with every energy cost at zero; {@link EnergyGameTests} restores the defaults.
 */
final class FreePower {
    private static final List<ModConfigSpec.IntValue> COSTS = List.of(FarmServerConfig.PUMP_ENERGY,
            FarmServerConfig.CROP_MONITOR_ENERGY, FarmServerConfig.CONTROLLER_ENERGY, FarmServerConfig.FARMBOT_STATION_ENERGY);

    private FreePower() {
    }

    static void enable() {
        for (ModConfigSpec.IntValue cost : COSTS) cost.set(0);
    }

    static void restoreDefaults() {
        for (ModConfigSpec.IntValue cost : COSTS) cost.set(cost.getDefault());
    }
}
