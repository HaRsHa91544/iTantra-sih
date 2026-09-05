package com.example.itantra_sih.wifidirect;

import android.util.Log;

import com.example.itantra_sih.transport.MessageTransport;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Length-prefixed message transport over Wi-Fi Direct local sockets.
 *
 * Frame format: 4-byte big-endian length, followed by that many UTF-8 bytes.
 * This removes the newline-splitting bug of the previous println/readLine
 * transport and supports arbitrary message content.
 *
 * Connection lifecycle events are delivered through {@link SocketEventListener}.
 * Inbound payloads are delivered through the {@link MessageTransport.InboundListener}
 * so that the coordinator (not the UI) parses and routes incoming messages.
 */
public class WifiDirectSocketManager implements MessageTransport {

    private static final String TAG = "WifiDirectSocket";
    public static final int DEFAULT_PORT = 8988;
    private static final int MAX_MESSAGE_BYTES = 256 * 1024;

    public interface SocketEventListener {
        void onSocketConnected(boolean isServer, String remoteAddress);
        void onSocketDisconnected();
        void onError(String errorMessage);
    }

    private final SocketEventListener connectionListener;
    private InboundListener inboundListener;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private DataInputStream dataInputStream;
    private DataOutputStream dataOutputStream;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();

    private Thread serverThread;
    private Thread clientThread;
    private Thread receiveThread;

    private volatile boolean isRunning = false;

    public WifiDirectSocketManager(SocketEventListener connectionListener) {
        this.connectionListener = connectionListener;
    }

    /**
     * Start as Group Owner (Host Server). Loops on accept() so the server
     * survives client disconnects and can accept a new connection.
     */
    public synchronized void startServer(int port) {
        stop();
        isRunning = true;
        serverThread = new Thread(() -> {
            try {
                Log.d(TAG, "Starting ServerSocket on port " + port);
                serverSocket = new ServerSocket(port);
                serverSocket.setReuseAddress(true);

                while (isRunning) {
                    Socket socket;
                    try {
                        socket = serverSocket.accept();
                    } catch (IOException e) {
                        if (isRunning) {
                            Log.w(TAG, "Server accept interrupted: " + e.getMessage());
                        }
                        break;
                    }
                    Log.d(TAG, "Client connected: " + socket.getRemoteSocketAddress());
                    clientSocket = socket;
                    setupStreams(socket);

                    if (connectionListener != null) {
                        connectionListener.onSocketConnected(true, socket.getRemoteSocketAddress().toString());
                    }
                    startReceiving();
                    // Wait until this client disconnects before accepting the next one.
                    synchronized (this) {
                        while (isRunning && clientSocket == socket) {
                            try {
                                wait(500);
                            } catch (InterruptedException e) {
                                break;
                            }
                        }
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Server error: " + e.getMessage(), e);
                    if (connectionListener != null) {
                        connectionListener.onError("Server socket error: " + e.getMessage());
                    }
                }
            }
        });
        serverThread.start();
    }

    /**
     * Start as Client connecting to Group Owner.
     */
    public synchronized void startClient(InetAddress hostAddress, int port) {
        stop();
        isRunning = true;
        clientThread = new Thread(() -> {
            int retries = 5;
            Socket socket = null;
            while (isRunning && retries > 0) {
                try {
                    Log.d(TAG, "Attempting connection to " + hostAddress.getHostAddress() + ":" + port);
                    socket = new Socket();
                    socket.bind(null);
                    socket.connect(new InetSocketAddress(hostAddress, port), 5000);
                    break;
                } catch (IOException e) {
                    retries--;
                    Log.w(TAG, "Connection failed, retries left: " + retries + " error: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {}
                }
            }

            if (!isRunning) {
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
                return;
            }

            if (socket != null && socket.isConnected()) {
                clientSocket = socket;
                try {
                    setupStreams(socket);
                    Log.d(TAG, "Connected to server successfully");
                    if (connectionListener != null) {
                        connectionListener.onSocketConnected(false, socket.getRemoteSocketAddress().toString());
                    }
                    startReceiving();
                } catch (IOException e) {
                    Log.e(TAG, "Failed setting up streams", e);
                    if (connectionListener != null) {
                        connectionListener.onError("Stream initialization failed: " + e.getMessage());
                    }
                }
            } else {
                if (connectionListener != null) {
                    connectionListener.onError("Failed to connect to Group Owner at " + hostAddress.getHostAddress());
                }
            }
        });
        clientThread.start();
    }

    private void setupStreams(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();
        dataInputStream = new DataInputStream(in);
        dataOutputStream = new DataOutputStream(out);
    }

    private void startReceiving() {
        receiveThread = new Thread(() -> {
            Socket currentSocket = clientSocket;
            try {
                while (isRunning && dataInputStream != null && clientSocket != null) {
                    int length = dataInputStream.readInt();
                    if (length < 0 || length > MAX_MESSAGE_BYTES) {
                        throw new IOException("Invalid message length: " + length);
                    }
                    byte[] payload = new byte[length];
                    dataInputStream.readFully(payload);
                    String message = new String(payload, StandardCharsets.UTF_8);
                    InboundListener current = inboundListener;
                    if (current != null) {
                        current.onPayloadReceived(message);
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.d(TAG, "Socket read terminated: " + e.getMessage());
                }
            } finally {
                synchronized (this) {
                    if (currentSocket != null && clientSocket == currentSocket) {
                        clientSocket = null;
                        notifyAll();
                    }
                }
                if (isRunning && connectionListener != null) {
                    connectionListener.onSocketDisconnected();
                }
            }
        });
        receiveThread.start();
    }

    /**
     * Sends a message to the connected peer over local Wi-Fi Direct socket.
     * First writes a 4-byte length prefix (big-endian) then the UTF-8 bytes.
     */
    public void sendMessage(String message) {
        sendExecutor.execute(() -> {
            try {
                if (dataOutputStream != null && clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed()) {
                    byte[] payload = message.getBytes(StandardCharsets.UTF_8);
                    if (payload.length > MAX_MESSAGE_BYTES) {
                        throw new IOException("Message too large: " + payload.length + " bytes");
                    }
                    dataOutputStream.writeInt(payload.length);
                    dataOutputStream.write(payload);
                    dataOutputStream.flush();
                } else {
                    Log.w(TAG, "Cannot send message, socket is not connected");
                    if (connectionListener != null) {
                        connectionListener.onError("Cannot send message: not connected");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message: " + e.getMessage(), e);
                if (connectionListener != null) {
                    connectionListener.onError("Failed to send message: " + e.getMessage());
                }
            }
        });
    }

    /**
     * Closes all connections, sockets, and threads.
     */
    public synchronized void stop() {
        isRunning = false;

        try {
            if (dataOutputStream != null) {
                dataOutputStream.close();
                dataOutputStream = null;
            }
        } catch (Exception ignored) {}

        try {
            if (dataInputStream != null) {
                dataInputStream.close();
                dataInputStream = null;
            }
        } catch (Exception ignored) {}

        try {
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                clientSocket = null;
            }
        } catch (Exception ignored) {}

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
                serverSocket = null;
            }
        } catch (Exception ignored) {}

        if (serverThread != null) {
            serverThread.interrupt();
            serverThread = null;
        }
        if (clientThread != null) {
            clientThread.interrupt();
            clientThread = null;
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
            receiveThread = null;
        }
        notifyAll();
    }

    /** Shuts down the send executor. Call before dropping the last reference. */
    public synchronized void shutdown() {
        stop();
        sendExecutor.shutdownNow();
    }

    // ==================== MessageTransport ====================

    @Override
    public void sendMessagePayload(String payload) {
        sendMessage(payload);
    }

    @Override
    public void setInboundListener(InboundListener listener) {
        this.inboundListener = listener;
    }

    @Override
    public void stopAndShutdown() {
        shutdown();
    }
}
