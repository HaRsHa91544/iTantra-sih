package com.example.itantra_sih.transport;

import java.net.InetAddress;

/**
 * Socket-level connection lifecycle, independent of any concrete Wi-Fi Direct
 * implementation. Lives in the neutral {@code transport} package so that neither
 * the Wi-Fi module nor the application/UI layer depends on the other.
 *
 * Payload delivery is defined separately in {@link MessageTransport}; this
 * interface only manages the link itself.
 */
public interface SocketConnection {

    int DEFAULT_PORT = 8988;

    /**
     * Connection lifecycle events. Implemented by the coordinator, never by the UI.
     */
    interface ConnectionListener {
        void onConnected(boolean isServer, String remoteAddress);
        void onDisconnected();
        void onError(String message);
    }

    void setConnectionListener(ConnectionListener listener);

    /** Start as Group Owner (host server). Specialists reconnect handling. */
    void startServer(int port);

    /** Connect as a client to the given Group Owner address. */
    void connectToServer(InetAddress hostAddress, int port);

    /** Stop the link (idempotent). */
    void stop();

    /** Stop the link and release all worker resources. */
    void shutdown();
}
