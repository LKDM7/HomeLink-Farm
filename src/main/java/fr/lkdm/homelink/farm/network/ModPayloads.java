package fr.lkdm.homelink.farm.network;

import fr.lkdm.homelink.farm.client.ClientPayloadHandlers;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    public static final String PROTOCOL_VERSION = "1";

    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(RenameFarmDevicePayload.TYPE, RenameFarmDevicePayload.STREAM_CODEC, RenameFarmDevicePayload::handle);
        registrar.playToServer(DeviceCommandPayload.TYPE, DeviceCommandPayload.STREAM_CODEC, DeviceCommandPayload::handle);
        registrar.playToServer(LocateProblemPayload.TYPE, LocateProblemPayload.STREAM_CODEC, LocateProblemPayload::handle);
        registrar.playToServer(IrrigationOverlayPayloads.Request.TYPE, IrrigationOverlayPayloads.Request.STREAM_CODEC,
                IrrigationOverlayPayloads::handleRequest);
        registrar.playToClient(IrrigationOverlayPayloads.Data.TYPE, IrrigationOverlayPayloads.Data.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.irrigationOverlay(payload));
        registrar.playToServer(HomeNetworkPayloads.Bind.TYPE, HomeNetworkPayloads.Bind.STREAM_CODEC, HomeNetworkPayloads::handleBind);
        registrar.playToClient(HomeNetworkPayloads.Choices.TYPE, HomeNetworkPayloads.Choices.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.networkChoices(payload));
        // Client handlers live in client-only code; the lambdas only resolve it when a packet arrives on a client.
        registrar.playToClient(MonitorProblemsPayload.TYPE, MonitorProblemsPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.monitorProblems(payload));
        registrar.playToClient(ShowMarkerPayload.TYPE, ShowMarkerPayload.STREAM_CODEC,
                (payload, context) -> ClientPayloadHandlers.showMarker(payload));
    }
}
