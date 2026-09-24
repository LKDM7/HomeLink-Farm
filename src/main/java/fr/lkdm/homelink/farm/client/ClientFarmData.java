package fr.lkdm.homelink.farm.client;

import fr.lkdm.homelink.farm.farm.diagnostic.CropProblem;
import fr.lkdm.homelink.farm.network.HomeNetworkPayloads;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;

/** Client cache of data sent only on demand (to players viewing a device screen). */
public final class ClientFarmData {
    private static final Map<BlockPos, List<CropProblem>> PROBLEMS = new HashMap<>();
    private static final Map<BlockPos, List<HomeNetworkPayloads.Choice>> NETWORK_CHOICES = new HashMap<>();

    private ClientFarmData() {
    }

    public static void setProblems(BlockPos monitor, List<CropProblem> problems) {
        PROBLEMS.put(monitor.immutable(), List.copyOf(problems));
    }

    public static List<CropProblem> problems(BlockPos monitor) {
        return PROBLEMS.getOrDefault(monitor, List.of());
    }

    public static void setNetworkChoices(BlockPos device, List<HomeNetworkPayloads.Choice> choices) {
        NETWORK_CHOICES.put(device.immutable(), List.copyOf(choices));
    }

    public static List<HomeNetworkPayloads.Choice> networkChoices(BlockPos device) {
        return NETWORK_CHOICES.getOrDefault(device, List.of());
    }

    public static void clear() {
        PROBLEMS.clear();
        NETWORK_CHOICES.clear();
    }
}
