package io.github.garfieldcoder.luxworks.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers the small, versioned control protocol used by Luxworks. */
public final class LuxworksNetworking {
    private LuxworksNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToServer(
                        SetServoTargetPayload.TYPE,
                        SetServoTargetPayload.STREAM_CODEC,
                        SetServoTargetPayload::handle
                )
                .playToServer(
                        ClearSavedTargetPayload.TYPE,
                        ClearSavedTargetPayload.STREAM_CODEC,
                        ClearSavedTargetPayload::handle
                );
    }
}
