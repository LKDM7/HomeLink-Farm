package fr.lkdm.homelink.farm.farm.controller;

import fr.lkdm.homelink.farm.blockentity.CropMonitorBlockEntity;
import fr.lkdm.homelink.farm.blockentity.FarmControllerBlockEntity;
import fr.lkdm.homelink.farm.blockentity.IrrigationPumpBlockEntity;
import fr.lkdm.homelink.farm.farm.crop.CropScanResult;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationManager;
import fr.lkdm.homelink.farm.farm.irrigation.IrrigationNetwork;
import fr.lkdm.homelink.farm.farm.irrigation.PumpSnapshot;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Builds a controller's {@link FarmSummary} from the cached results of its components.
 * Never scans the world and never loads chunks: a component in an unloaded chunk contributes
 * its last known figures (counted as stale) or, if none are known, is counted unavailable.
 * Two linked pumps of the same network count its sprinklers and capacity once.
 */
public final class FarmAggregator {
    private FarmAggregator() {
    }

    public static FarmSummary aggregate(ServerLevel level, FarmControllerBlockEntity controller) {
        FarmSummary.Builder builder = new FarmSummary.Builder();
        Set<IrrigationNetwork> networks = Collections.newSetFromMap(new IdentityHashMap<>());
        IrrigationManager irrigation = IrrigationManager.get(level);
        LastKnownFigures cache = controller.lastKnown();
        cache.retainOnly(controller.linkedComponents());
        for (LinkedComponent entry : controller.linkedComponents().all()) {
            BlockEntity blockEntity = level.isLoaded(entry.pos()) ? level.getBlockEntity(entry.pos()) : null;
            boolean live = blockEntity instanceof FarmComponent component && component.componentId().equals(entry.id());
            if (entry.kind() == FarmComponentKind.FARMBOT_STATION) {
                // Stations carry no crop or irrigation figures.
                continue;
            }
            if (live && blockEntity instanceof CropMonitorBlockEntity monitor && monitor.result().isPresent()) {
                CropScanResult result = monitor.result().get();
                cache.remember(entry.id(), result);
                builder.addMonitor(result);
            } else if (live && blockEntity instanceof IrrigationPumpBlockEntity pump) {
                PumpSnapshot snapshot = pump.snapshot();
                cache.remember(entry.id(), snapshot);
                builder.addPump(snapshot.status().isFailure());
                irrigation.networkAt(entry.pos()).ifPresent(network -> {
                    if (networks.add(network)) builder.addNetwork(network.sprinklers().size(), network.capacity());
                });
            } else if (!level.isLoaded(entry.pos()) && cache.monitor(entry.id()).isPresent()) {
                builder.addMonitor(cache.monitor(entry.id()).get());
                builder.addStale();
            } else if (!level.isLoaded(entry.pos()) && cache.pump(entry.id()).isPresent()) {
                PumpSnapshot snapshot = cache.pump(entry.id()).get();
                builder.addPump(snapshot.status().isFailure());
                builder.addNetwork(snapshot.sprinklers(), snapshot.capacity());
                builder.addStale();
            } else {
                builder.addUnavailable();
            }
        }
        return builder.build();
    }
}
