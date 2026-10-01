package fr.lkdm.homelink.farm.homelink;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.lkdm.homecore.api.action.ActionContext;
import fr.lkdm.homecore.api.action.ActionResult;
import fr.lkdm.homecore.api.device.DeviceStatus;
import fr.lkdm.homecore.api.event.DeviceEvent;
import fr.lkdm.homecore.api.metric.Percentage;
import fr.lkdm.homecore.api.registry.DeviceRegistry;
import fr.lkdm.homelink.farm.farm.controller.FarmSummary;
import fr.lkdm.homelink.farm.farm.diagnostic.ProblemCounts;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Exercises the adapter against the real HomeCore public registry, schema and action contracts. */
class FarmControllerDeviceTest {
    private static final class FakeController implements FarmControllerView {
        UUID id = UUID.randomUUID();
        boolean operational = true;
        String name = "Main Farm";
        FarmSummary summary = FarmSummary.EMPTY;
        int rescans;

        @Override public UUID deviceId() { return id; }
        @Override public Component displayName() { return Component.literal(name); }
        @Override public void setCustomName(String value) { name = value.isEmpty() ? "Farm Controller" : value; }
        @Override public boolean isOperational() { return operational; }
        @Override public Optional<BlockPos> devicePosition() { return Optional.of(new BlockPos(1, 2, 3)); }
        @Override public Optional<ResourceKey<Level>> deviceDimension() { return Optional.of(Level.OVERWORLD); }
        @Override public FarmSummary summary() { return summary; }
        @Override public void requestRescan() { rescans++; }
    }

    static FarmSummary summary(int crops, int ready, int irrigated, int problems, int pumpFaults) {
        int[] counts = new int[fr.lkdm.homelink.farm.farm.diagnostic.ProblemType.values().length];
        counts[0] = problems;
        return new FarmSummary(1, crops, ready, crops == 0 ? 0 : ready / (float) crops, crops, irrigated,
                ProblemCounts.of(counts), 1, pumpFaults, 5, 5, 0, 0, false);
    }

    @Test
    void registersInHomeCoreRegistryWithStableIdentity() {
        FakeController source = new FakeController();
        FarmControllerDevice device = new FarmControllerDevice(source, event -> { });
        DeviceRegistry registry = new DeviceRegistry();
        registry.register(device);
        assertEquals(source.id, device.id());
        assertEquals(FarmIds.FARM_CONTROLLER, device.deviceType());
        assertEquals(DeviceStatus.State.ONLINE, device.status().state());
        assertEquals("Main Farm", device.displayName().getString());
        assertTrue(device.schema().actions().stream().anyMatch(action -> action.id().equals(fr.lkdm.homecore.api.action.StandardActions.RENAME)));
        assertTrue(device.rename("North field").isSuccess());
        assertEquals("North field", device.displayName().getString());
        assertEquals(1, registry.findByType(FarmIds.FARM_CONTROLLER).size());
        assertTrue(device.schema().metrics().stream().anyMatch(metric -> metric.id().equals(FarmIds.CROP_COUNT)));
        assertTrue(device.schema().events().containsAll(List.of(FarmIds.CROP_READY, FarmIds.PROBLEM_DETECTED)));
    }

    @Test
    void metricsFollowTheSummary() {
        FakeController source = new FakeController();
        FarmControllerDevice device = new FarmControllerDevice(source, event -> { });
        device.refresh(summary(384, 247, 367, 6, 0));
        assertEquals(384, value(device, FarmIds.CROP_COUNT));
        assertEquals(new Percentage(64.3), value(device, FarmIds.READY_PERCENTAGE));
        assertEquals(new Percentage(95.6), value(device, FarmIds.IRRIGATION_COVERAGE));
        assertEquals(367, value(device, FarmIds.IRRIGATED_CROPS));
        assertEquals(6, value(device, FarmIds.PROBLEM_COUNT));
        assertEquals(new Percentage(33), value(device, FarmIds.GROWTH_BONUS));
    }

    @Test
    void rescanActionIsExposedAndValidated() {
        FakeController source = new FakeController();
        FarmControllerDevice device = new FarmControllerDevice(source, event -> { });
        var action = device.actions().getFirst();
        assertEquals(FarmIds.ACTION_RESCAN, action.id());
        ActionContext context = new ActionContext(UUID.randomUUID(), UUID.randomUUID(), device.id());
        assertEquals(ActionResult.Code.SUCCESS, action.execute(context, fr.lkdm.homecore.api.action.Unit.INSTANCE).code());
        assertEquals(1, source.rescans);
        assertFalse(action.execute(context, "wrong type").isSuccess());
    }

    @Test
    void eventsFireOnTransitionsOnly() {
        FakeController source = new FakeController();
        List<DeviceEvent> published = new ArrayList<>();
        FarmControllerDevice device = new FarmControllerDevice(source, published::add);
        device.refresh(summary(100, 10, 0, 0, 0));        // first observation: silent
        device.refresh(summary(100, 95, 0, 0, 0));        // ready crosses 90%
        device.refresh(summary(100, 96, 0, 0, 0));        // still ready: no repeat
        device.refresh(summary(100, 96, 0, 3, 1));        // problems + pump fault appear
        device.refresh(summary(100, 96, 0, 4, 1));        // still failing: no repeat
        device.refresh(summary(100, 96, 0, 4, 0));        // pump restored
        List<Object> types = published.stream().map(DeviceEvent::type).map(Object.class::cast).toList();
        assertEquals(List.of(FarmIds.CROP_READY, FarmIds.PROBLEM_DETECTED, FarmIds.IRRIGATION_FAILURE, FarmIds.IRRIGATION_RESTORED), types);
        assertTrue(published.stream().allMatch(event -> event.source().equals(device.id())));
    }

    @Test
    void cropReadyRearmsWithHysteresis() {
        TransitionTracker tracker = new TransitionTracker(UUID.randomUUID());
        java.time.Instant now = java.time.Instant.now();
        tracker.controller(summary(100, 0, 0, 0, 0), 0.9F, now);
        assertEquals(1, tracker.controller(summary(100, 91, 0, 0, 0), 0.9F, now).size());
        assertEquals(0, tracker.controller(summary(100, 80, 0, 0, 0), 0.9F, now).size()); // not below 70%
        assertEquals(0, tracker.controller(summary(100, 92, 0, 0, 0), 0.9F, now).size()); // not re-armed
        tracker.controller(summary(100, 10, 0, 0, 0), 0.9F, now);                           // harvested: re-arm
        assertEquals(1, tracker.controller(summary(100, 95, 0, 0, 0), 0.9F, now).size());
    }

    @Test
    void pumpTransitions() {
        TransitionTracker tracker = new TransitionTracker(UUID.randomUUID());
        java.time.Instant now = java.time.Instant.now();
        var status = fr.lkdm.homelink.farm.farm.irrigation.PumpStatus.class;
        assertEquals(0, tracker.pump(fr.lkdm.homelink.farm.farm.irrigation.PumpStatus.ACTIVE, 5, 5, now).size());
        var overloaded = tracker.pump(fr.lkdm.homelink.farm.farm.irrigation.PumpStatus.OVER_CAPACITY, 6, 5, now);
        assertEquals(List.of(FarmIds.PUMP_OVER_CAPACITY, FarmIds.IRRIGATION_FAILURE), overloaded.stream().map(DeviceEvent::type).toList());
        assertEquals(0, tracker.pump(fr.lkdm.homelink.farm.farm.irrigation.PumpStatus.OVER_CAPACITY, 6, 5, now).size());
        var restored = tracker.pump(fr.lkdm.homelink.farm.farm.irrigation.PumpStatus.ACTIVE, 5, 5, now);
        assertEquals(List.of(FarmIds.IRRIGATION_RESTORED), restored.stream().map(DeviceEvent::type).toList());
        assertTrue(status.isEnum());
    }

    @Test
    void removedSourceIsInvalidAndPrunedByHomeCore() {
        FakeController source = new FakeController();
        FarmControllerDevice device = new FarmControllerDevice(source, event -> { });
        DeviceRegistry registry = new DeviceRegistry();
        registry.register(device);
        source.operational = false;
        assertFalse(device.isValid());
        assertEquals(DeviceStatus.State.OFFLINE, device.status().state());
        assertTrue(registry.get(source.id).isEmpty());
    }

    @Test
    void homeCoreRejectsDuplicateIdentity() {
        FakeController source = new FakeController();
        DeviceRegistry registry = new DeviceRegistry();
        registry.register(new FarmControllerDevice(source, event -> { }));
        assertThrows(IllegalArgumentException.class, () -> registry.register(new FarmControllerDevice(source, event -> { })));
    }

    private static Object value(FarmControllerDevice device, net.minecraft.resources.ResourceLocation id) {
        return device.metrics().stream().filter(metric -> metric.id().equals(id)).findFirst().orElseThrow().value();
    }
}
