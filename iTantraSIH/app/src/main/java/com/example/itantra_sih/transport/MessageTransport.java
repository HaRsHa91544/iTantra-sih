package com.example.itantra_sih.transport;

/**
 * Neutral transport interface connecting the Wi-Fi Direct module to the
 * application coordinator.
 *
 * Lives in its own package so that neither the Wi-Fi module nor the
 * application layer depends on one another. The coordinator only knows
 * this interface; the Wi-Fi Direct implementation only knows this
 * interface.
 */
public interface MessageTransport {

    /**
     * Inbound messages delivered by the transport. Implemented by the
     * coordinator, never by the UI.
     */
    interface InboundListener {
        void onPayloadReceived(String payload);
    }

    /**
     * Send a serialized message payload to the connected peer.
     */
    void sendMessagePayload(String payload);

    /**
     * Register the receiver for inbound payloads.
     */
    void setInboundListener(InboundListener listener);

    /**
     * Stop the transport and release all resources.
     */
    void stopAndShutdown();
}