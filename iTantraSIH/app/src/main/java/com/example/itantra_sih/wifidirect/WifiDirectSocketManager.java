package com.example.itantra_sih.wifidirect;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WifiDirectSocketManager {

    private static final String TAG = "WifiDirectSocket";
    public static final int DEFAULT_PORT = 8988;

    public interface SocketEventListener {
        void onSocketConnected(boolean isServer, String remoteAddress);
        void onMessageReceived(String message);
        void onSocketDisconnected();
        void onError(String errorMessage);
    }

    private final SocketEventListener listener;
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter printWriter;
    private BufferedReader bufferedReader;
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor();

    private Thread serverThread;
    private Thread clientThread;
    private Thread receiveThread;

    private volatile boolean isRunning = false;

    public WifiDirectSocketManager(SocketEventListener listener) {
        this.listener = listener;
    }

    /**
     * Start as Group Owner (Host Server)
     */
    public synchronized void startServer(int port) {
        stop();
        isRunning = true;
        serverThread = new Thread(() -> {
            try {
                Log.d(TAG, "Starting ServerSocket on port " + port);
                serverSocket = new ServerSocket(port);
                serverSocket.setReuseAddress(true);

                Socket socket = serverSocket.accept();
                Log.d(TAG, "Client connected: " + socket.getRemoteSocketAddress());
                clientSocket = socket;

                setupStreams(socket);

                if (listener != null) {
                    listener.onSocketConnected(true, socket.getRemoteSocketAddress().toString());
                }

                startReceiving();

            } catch (IOException e) {
                if (isRunning) {
                    Log.e(TAG, "Server error: " + e.getMessage(), e);
                    if (listener != null) {
                        listener.onError("Server socket error: " + e.getMessage());
                    }
                }
            }
        });
        serverThread.start();
    }

    /**
     * Start as Client connecting to Group Owner
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
                    if (listener != null) {
                        listener.onSocketConnected(false, socket.getRemoteSocketAddress().toString());
                    }
                    startReceiving();
                } catch (IOException e) {
                    Log.e(TAG, "Failed setting up streams", e);
                    if (listener != null) {
                        listener.onError("Stream initialization failed: " + e.getMessage());
                    }
                }
            } else {
                if (listener != null) {
                    listener.onError("Failed to connect to Group Owner at " + hostAddress.getHostAddress());
                }
            }
        });
        clientThread.start();
    }

    private void setupStreams(Socket socket) throws IOException {
        bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        printWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)), true);
    }

    private void startReceiving() {
        receiveThread = new Thread(() -> {
            try {
                String line;
                while (isRunning && bufferedReader != null && (line = bufferedReader.readLine()) != null) {
                    if (listener != null) {
                        listener.onMessageReceived(line);
                    }
                }
            } catch (IOException e) {
                if (isRunning) {
                    Log.d(TAG, "Socket read terminated: " + e.getMessage());
                }
            } finally {
                if (isRunning && listener != null) {
                    listener.onSocketDisconnected();
                }
            }
        });
        receiveThread.start();
    }

    /**
     * Sends a message to the connected peer over local Wi-Fi Direct socket.
     */
    public void sendMessage(String message) {
        sendExecutor.execute(() -> {
            try {
                if (printWriter != null && clientSocket != null && clientSocket.isConnected() && !clientSocket.isClosed()) {
                    printWriter.println(message);
                    printWriter.flush();
                } else {
                    Log.w(TAG, "Cannot send message, socket is not connected");
                    if (listener != null) {
                        listener.onError("Cannot send message: not connected");
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error sending message: " + e.getMessage(), e);
                if (listener != null) {
                    listener.onError("Failed to send message: " + e.getMessage());
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
            if (printWriter != null) {
                printWriter.close();
                printWriter = null;
            }
        } catch (Exception ignored) {}

        try {
            if (bufferedReader != null) {
                bufferedReader.close();
                bufferedReader = null;
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
    }
}