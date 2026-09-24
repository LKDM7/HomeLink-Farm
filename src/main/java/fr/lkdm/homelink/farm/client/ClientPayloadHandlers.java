package fr.lkdm.homelink.farm.client;

import fr.lkdm.homelink.farm.client.overlay.IrrigationOverlay;
import fr.lkdm.homelink.farm.client.rendering.LocateMarkers;
import fr.lkdm.homelink.farm.network.IrrigationOverlayPayloads;
import fr.lkdm.homelink.farm.network.MonitorProblemsPayload;
import fr.lkdm.homelink.farm.network.ShowMarkerPayload;

/** Client-side packet handlers (only ever invoked on a physical client). */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void monitorProblems(MonitorProblemsPayload payload) {
        ClientFarmData.setProblems(payload.monitor(), payload.problems());
    }

    public static void showMarker(ShowMarkerPayload payload) {
        LocateMarkers.add(payload.pos(), payload.color(), payload.durationTicks());
    }

    public static void networkChoices(fr.lkdm.homelink.farm.network.HomeNetworkPayloads.Choices payload) {
        ClientFarmData.setNetworkChoices(payload.device(), payload.choices());
    }

    public static void irrigationOverlay(IrrigationOverlayPayloads.Data payload) {
        IrrigationOverlay.accept(payload);
    }
}
