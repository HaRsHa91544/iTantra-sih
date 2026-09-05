package com.example.itantra_sih;

import android.Manifest;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.itantra_sih.speech.stt.OfflineSTT;
import com.example.itantra_sih.speech.stt.STTEngine;
import com.example.itantra_sih.wifidirect.DeviceAdapter;
import com.example.itantra_sih.wifidirect.WifiDirectBroadcastReceiver;
import com.example.itantra_sih.wifidirect.WifiDirectSocketManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        WifiDirectBroadcastReceiver.WifiDirectEventListener,
        WifiDirectSocketManager.SocketEventListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    // Wi-Fi Direct UI elements
    private TextView tvConnectionStatus;
    private TextView tvMyDeviceDetails;
    private TextView tvRoleDetails;
    private TextView tvEmptyDevices;
    private Button btnDiscover;
    private Button btnDisconnect;
    private ListView lvDevices;

    // Received Data & Speech UI elements
    private TextView tvModelStatus;
    private ScrollView scrollReceivedData;
    private TextView tvReceivedData;
    private TextView tvSpeakingStatus;
    private Button btnToggleSpeaking;

    // Speech-to-Text Engine state
    private STTEngine sttEngine;
    private boolean isRecording = false;
    private boolean isModelReady = false;
    private boolean isWifiConnected = false;
    private final StringBuilder receivedDataHistory = new StringBuilder();

    // Wi-Fi Direct objects
    private WifiP2pManager wifiP2pManager;
    private WifiP2pManager.Channel channel;
    private WifiDirectBroadcastReceiver receiver;
    private IntentFilter intentFilter;

    // Devices list
    private final List<WifiP2pDevice> peerList = new ArrayList<>();
    private DeviceAdapter deviceAdapter;

    // Offline Socket Manager
    private WifiDirectSocketManager socketManager;

    private boolean isReceiverRegistered = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        initWifiDirect();
        initSTTEngine();
        setupListeners();
        checkAndRequestPermissions();
    }

    private void initViews() {
        // Status & Connection Views
        tvConnectionStatus = findViewById(R.id.tvConnectionStatus);
        tvMyDeviceDetails = findViewById(R.id.tvMyDeviceDetails);
        tvRoleDetails = findViewById(R.id.tvRoleDetails);
        tvEmptyDevices = findViewById(R.id.tvEmptyDevices);
        btnDiscover = findViewById(R.id.btnDiscover);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        lvDevices = findViewById(R.id.lvDevices);

        // Received Data & Speech Views
        tvModelStatus = findViewById(R.id.tvModelStatus);
        scrollReceivedData = findViewById(R.id.scrollReceivedData);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        tvSpeakingStatus = findViewById(R.id.tvSpeakingStatus);
        btnToggleSpeaking = findViewById(R.id.btnToggleSpeaking);

        // Speak button is disabled until devices are connected
        updateSpeakButtonState();

        deviceAdapter = new DeviceAdapter(this, peerList);
        lvDevices.setAdapter(deviceAdapter);

        socketManager = new WifiDirectSocketManager(this);
    }

    private void initWifiDirect() {
        wifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (wifiP2pManager != null) {
            channel = wifiP2pManager.initialize(this, getMainLooper(), () -> {
                Log.w(TAG, "Wi-Fi Direct channel lost. Reinitializing...");
                initWifiDirect();
            });
        }

        receiver = new WifiDirectBroadcastReceiver(wifiP2pManager, channel, this);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }

    private void initSTTEngine() {
        sttEngine = new OfflineSTT();
        sttEngine.setOnResultListener(new STTEngine.OnResultListener() {
            @Override
            public void onPartialResult(String hypothesis) {
                runOnUiThread(() -> {
                    if (!hypothesis.isEmpty()) {
                        tvSpeakingStatus.setVisibility(View.VISIBLE);
                        tvSpeakingStatus.setText("Listening: " + hypothesis + "...");
                    }
                });
            }

            @Override
            public void onFinalResult(String text) {
                runOnUiThread(() -> {
                    String spoken = text.trim();
                    if (!spoken.isEmpty()) {
                        tvSpeakingStatus.setVisibility(View.VISIBLE);
                        tvSpeakingStatus.setText("Sent: \"" + spoken + "\"");

                        // Convert voice input into text and send to another mobile through Wi-Fi Direct
                        if (socketManager != null) {
                            socketManager.sendMessage(spoken);
                        }
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "STT Engine error", e);
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "STT Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        tvModelStatus.setText("Model: Loading...");
        sttEngine.init(this, new STTEngine.OnInitListener() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    isModelReady = true;
                    tvModelStatus.setText("Model: Ready");
                    updateSpeakButtonState();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    tvModelStatus.setText("Model: Failed");
                    Toast.makeText(MainActivity.this, "Failed to load speech model: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void setupListeners() {
        btnDiscover.setOnClickListener(v -> startPeerDiscovery());

        btnDisconnect.setOnClickListener(v -> disconnectFromPeer());

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            WifiP2pDevice device = peerList.get(position);
            confirmAndConnect(device);
        });

        // Speak Button: Takes voice input, converts to text, and sends via Wi-Fi Direct
        btnToggleSpeaking.setOnClickListener(v -> {
            if (!isWifiConnected) {
                Toast.makeText(this, "Connect to another device via Wi-Fi Direct first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isModelReady) {
                Toast.makeText(this, "Speech model is still loading, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isRecording) {
                stopSpeaking();
            } else {
                checkMicrophonePermissionAndStart();
            }
        });
    }

    /**
     * Updates the Speak button state based on Wi-Fi Direct connection and speech model readiness.
     * The button is enabled ONLY when two users are connected.
     */
    private void updateSpeakButtonState() {
        if (!isWifiConnected) {
            btnToggleSpeaking.setEnabled(false);
            btnToggleSpeaking.setText("Start speaking");
            if (isRecording) {
                stopSpeaking();
            }
        } else if (!isModelReady) {
            btnToggleSpeaking.setEnabled(false);
            btnToggleSpeaking.setText("Loading speech model...");
        } else {
            btnToggleSpeaking.setEnabled(true);
            btnToggleSpeaking.setText(isRecording ? "Stop Speaking" : "Start speaking");
        }
    }

    /**
     * Asks for RECORD_AUDIO permission ONLY when the user clicks 'Start speaking'.
     */
    private void checkMicrophonePermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_RECORD_AUDIO_PERMISSION
            );
        } else {
            startSpeaking();
        }
    }

    private void startSpeaking() {
        if (sttEngine != null) {
            sttEngine.start();
        }
        isRecording = true;
        updateSpeakButtonState();
        tvSpeakingStatus.setVisibility(View.VISIBLE);
        tvSpeakingStatus.setText("Listening... Speak now");
    }

    private void stopSpeaking() {
        isRecording = false;
        if (sttEngine != null) {
            sttEngine.stop();
        }
        updateSpeakButtonState();
    }

    /**
     * Check and request runtime permissions for Wi-Fi Direct (NEARBY_WIFI_DEVICES / LOCATION).
     * RECORD_AUDIO is requested on demand when the user clicks 'Start speaking'.
     */
    private void checkAndRequestPermissions() {
        List<String> requiredPermissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                requiredPermissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!requiredPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    requiredPermissions.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted for Wi-Fi Direct", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Wi-Fi Direct permissions are required for discovery", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeaking();
            } else {
                Toast.makeText(this, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startPeerDiscovery() {
        if (wifiP2pManager == null || channel == null) {
            Toast.makeText(this, "Wi-Fi Direct is not supported on this device", Toast.LENGTH_SHORT).show();
            return;
        }

        checkAndRequestPermissions();

        try {
            tvEmptyDevices.setText("Searching for nearby devices...");
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Discovery started", Toast.LENGTH_SHORT).show();
                        btnDiscover.setEnabled(false);
                        btnDiscover.postDelayed(() -> btnDiscover.setEnabled(true), 10000);
                    });
                }

                @Override
                public void onFailure(int reasonCode) {
                    runOnUiThread(() -> {
                        String reason = getFailureReason(reasonCode);
                        Toast.makeText(MainActivity.this, "Discovery failed: " + reason, Toast.LENGTH_SHORT).show();
                        tvEmptyDevices.setText("Discovery failed (" + reason + "). Make sure Wi-Fi & Location are enabled.");
                    });
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during discoverPeers", e);
            Toast.makeText(this, "Permission missing for peer discovery", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmAndConnect(WifiP2pDevice device) {
        String deviceName = (device.deviceName != null && !device.deviceName.isEmpty())
                ? device.deviceName : device.deviceAddress;

        new AlertDialog.Builder(this)
                .setTitle("Connect to Device")
                .setMessage("Do you want to connect to " + deviceName + " via Wi-Fi Direct?")
                .setPositiveButton("Connect", (dialog, which) -> connectToDevice(device))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void connectToDevice(WifiP2pDevice device) {
        if (wifiP2pManager == null || channel == null) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        runOnUiThread(() -> {
            tvConnectionStatus.setText("Connecting...");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_discovering));
        });

        try {
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Connection initiated to " + device.deviceName, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(int reasonCode) {
                    runOnUiThread(() -> {
                        String reason = getFailureReason(reasonCode);
                        Toast.makeText(MainActivity.this, "Connection failed: " + reason, Toast.LENGTH_SHORT).show();
                        tvConnectionStatus.setText("Disconnected");
                        tvConnectionStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_disconnected));
                    });
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during connect", e);
            Toast.makeText(this, "Permission missing to connect to peer", Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectFromPeer() {
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    onDisconnected();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Disconnected successfully", Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(int reasonCode) {
                    Log.w(TAG, "removeGroup failed with code: " + reasonCode);
                    onDisconnected();
                }
            });
        }
        if (socketManager != null) {
            socketManager.stop();
        }
    }

    // ==================== WifiDirectEventListener Callbacks ====================

    @Override
    public void onWifiDirectEnabled(boolean isEnabled) {
        runOnUiThread(() -> {
            if (!isEnabled) {
                tvConnectionStatus.setText("Wi-Fi Disabled");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
                Toast.makeText(this, "Please enable Wi-Fi for Wi-Fi Direct", Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public void onPeersDiscovered(Collection<WifiP2pDevice> peers) {
        runOnUiThread(() -> {
            peerList.clear();
            if (peers != null && !peers.isEmpty()) {
                peerList.addAll(peers);
                tvEmptyDevices.setVisibility(View.GONE);
                lvDevices.setVisibility(View.VISIBLE);
            } else {
                tvEmptyDevices.setText("No nearby Wi-Fi Direct devices found.");
                tvEmptyDevices.setVisibility(View.VISIBLE);
            }
            deviceAdapter.notifyDataSetChanged();
        });
    }

    /**
     * Called when Wi-Fi Direct connection info is available.
     * Updates UI on the UI thread to display "Connected" and enables Speak.
     */
    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        if (wifiP2pInfo != null && wifiP2pInfo.groupFormed) {
            isWifiConnected = true;
            runOnUiThread(() -> {
                tvConnectionStatus.setText("Connected");
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));

                btnDisconnect.setEnabled(true);
                updateSpeakButtonState();

                if (receivedDataHistory.length() == 0) {
                    tvReceivedData.setText("Connected! Waiting for speech data from peer...\n");
                }

                if (wifiP2pInfo.isGroupOwner) {
                    tvRoleDetails.setText("Role: Group Owner (Host) | Local IP: " +
                            (wifiP2pInfo.groupOwnerAddress != null ? wifiP2pInfo.groupOwnerAddress.getHostAddress() : "N/A"));
                    // Start host server socket completely offline
                    socketManager.startServer(WifiDirectSocketManager.DEFAULT_PORT);
                } else {
                    tvRoleDetails.setText("Role: Client | Host IP: " +
                            (wifiP2pInfo.groupOwnerAddress != null ? wifiP2pInfo.groupOwnerAddress.getHostAddress() : "N/A"));
                    // Connect client socket directly to host IP completely offline
                    if (wifiP2pInfo.groupOwnerAddress != null) {
                        socketManager.startClient(wifiP2pInfo.groupOwnerAddress, WifiDirectSocketManager.DEFAULT_PORT);
                    }
                }
            });
        }
    }

    @Override
    public void onDisconnected() {
        isWifiConnected = false;
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Disconnected");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            tvRoleDetails.setText("Role: Not connected");
            btnDisconnect.setEnabled(false);
            updateSpeakButtonState();
            tvSpeakingStatus.setVisibility(View.GONE);
            tvReceivedData.setText("Disconnected. Connect to another device to communicate.\n");
        });

        if (socketManager != null) {
            socketManager.stop();
        }
    }

    @Override
    public void onThisDeviceChanged(WifiP2pDevice device) {
        runOnUiThread(() -> {
            if (device != null) {
                String name = (device.deviceName != null && !device.deviceName.isEmpty())
                        ? device.deviceName : "Unknown";
                tvMyDeviceDetails.setText("My Device: " + name + " (" + device.deviceAddress + ")");
            }
        });
    }

    // ==================== SocketEventListener Callbacks ====================

    @Override
    public void onSocketConnected(boolean isServer, String remoteAddress) {
        isWifiConnected = true;
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Connected (Offline Socket Active)");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            updateSpeakButtonState();
        });
    }

    @Override
    public void onMessageReceived(String message) {
        runOnUiThread(() -> {
            // Displays received text from the peer mobile under "Received Data"
            appendReceivedData(message);
        });
    }

    @Override
    public void onSocketDisconnected() {
        runOnUiThread(() -> updateSpeakButtonState());
    }

    @Override
    public void onError(String errorMessage) {
        runOnUiThread(() -> Log.e(TAG, "Socket error: " + errorMessage));
    }

    private void appendReceivedData(String message) {
        if (receivedDataHistory.length() > 0) {
            receivedDataHistory.append("\n\n");
        }
        receivedDataHistory.append(message);
        tvReceivedData.setText(receivedDataHistory.toString());
        scrollReceivedData.post(() -> scrollReceivedData.fullScroll(View.FOCUS_DOWN));
    }

    private static String getFailureReason(int reasonCode) {
        switch (reasonCode) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                return "Wi-Fi Direct not supported";
            case WifiP2pManager.ERROR:
                return "Internal Error";
            case WifiP2pManager.BUSY:
                return "Framework Busy";
            default:
                return "Error code " + reasonCode;
        }
    }

    // ==================== Lifecycle ====================

    @Override
    protected void onResume() {
        super.onResume();
        if (!isReceiverRegistered && receiver != null) {
            ContextCompat.registerReceiver(
                    this,
                    receiver,
                    intentFilter,
                    ContextCompat.RECEIVER_EXPORTED
            );
            isReceiverRegistered = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isReceiverRegistered && receiver != null) {
            unregisterReceiver(receiver);
            isReceiverRegistered = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isRecording) {
            stopSpeaking();
        }
        if (sttEngine != null) {
            sttEngine.destroy();
            sttEngine = null;
        }
        if (socketManager != null) {
            socketManager.stop();
        }
        if (wifiP2pManager != null && channel != null) {
            try {
                wifiP2pManager.removeGroup(channel, null);
            } catch (Exception ignored) {}
        }
    }
}