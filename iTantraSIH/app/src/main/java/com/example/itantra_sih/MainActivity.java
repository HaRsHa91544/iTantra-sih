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

import com.example.itantra_sih.application.VoiceCommunication;
import com.example.itantra_sih.speech.stt.OfflineSTT;
import com.example.itantra_sih.speech.stt.STTEngine;
import com.example.itantra_sih.speech.tts.PiperEngine;
import com.example.itantra_sih.speech.tts.TTSEngine;
import com.example.itantra_sih.wifidirect.DeviceAdapter;
import com.example.itantra_sih.wifidirect.WifiDirectBroadcastReceiver;
import com.example.itantra_sih.wifidirect.WifiDirectSocketManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Splash screen to the whole voice-to-voice loop. Owns ONLY UI wiring:
 * it binds views, delegates STT/TTS/Wi-Fi control to VoiceCommunication,
 * and forwards Wi-Fi Direct connection lifecycle events to the UI.
 *
 * The actual voice pipeline lives in VoiceCommunication:
 *   Phone A: speak -> STT -> Message -> Wi-Fi
 *   Phone B: Wi-Fi -> Message -> TTS -> speak
 */
public class MainActivity extends AppCompatActivity implements
        WifiDirectBroadcastReceiver.WifiDirectEventListener,
        VoiceCommunication.Listener {

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
    private TTSEngine ttsEngine;
    private VoiceCommunication voiceCommunication;
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
        initEngines();
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

    private void initEngines() {
        // Black boxes are created here as a composition root and handed to
        // the coordinator; the UI never touches the modules directly.
        sttEngine = new OfflineSTT();
        ttsEngine = new PiperEngine(this);
        WifiDirectSocketManager link = new WifiDirectSocketManager();

        voiceCommunication = new VoiceCommunication(sttEngine, ttsEngine, link, link);
        voiceCommunication.setListener(this);

        tvModelStatus.setText(R.string.model_loading);
        sttEngine.init(this, new STTEngine.OnInitListener() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    isModelReady = true;
                    tvModelStatus.setText(R.string.model_ready);
                    updateSpeakButtonState();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    tvModelStatus.setText(R.string.model_failed);
                    Toast.makeText(MainActivity.this,
                            getString(R.string.model_failed_detail, String.valueOf(e.getMessage())),
                            Toast.LENGTH_SHORT).show();
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

        // Speak Button: Takes voice input via STT, sends through the coordinator
        btnToggleSpeaking.setOnClickListener(v -> {
            if (!isWifiConnected) {
                Toast.makeText(this, R.string.need_wifi_connection, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isModelReady) {
                Toast.makeText(this, R.string.model_still_loading, Toast.LENGTH_SHORT).show();
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
            btnToggleSpeaking.setText(R.string.start_speaking);
            if (isRecording) {
                stopSpeaking();
            }
        } else if (!isModelReady) {
            btnToggleSpeaking.setEnabled(false);
            btnToggleSpeaking.setText(R.string.model_loading_speech);
        } else {
            btnToggleSpeaking.setEnabled(true);
            btnToggleSpeaking.setText(isRecording ? R.string.stop_speaking : R.string.start_speaking);
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
        if (voiceCommunication != null) {
            voiceCommunication.startListening();
        }
        isRecording = true;
        updateSpeakButtonState();
        tvSpeakingStatus.setVisibility(View.VISIBLE);
        tvSpeakingStatus.setText(R.string.listening_speak);
    }

    private void stopSpeaking() {
        isRecording = false;
        if (voiceCommunication != null) {
            voiceCommunication.stopListening();
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
                Toast.makeText(this, R.string.permissions_granted, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, R.string.permissions_required, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeaking();
            } else {
                Toast.makeText(this, R.string.mic_permission_required, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void startPeerDiscovery() {
        if (wifiP2pManager == null || channel == null) {
            Toast.makeText(this, R.string.wifi_p2p_unsupported_device, Toast.LENGTH_SHORT).show();
            return;
        }

        checkAndRequestPermissions();

        try {
            tvEmptyDevices.setText(R.string.searching_devices);
            wifiP2pManager.discoverPeers(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, R.string.discovery_started, Toast.LENGTH_SHORT).show();
                        btnDiscover.setEnabled(false);
                        btnDiscover.postDelayed(() -> btnDiscover.setEnabled(true), 10000);
                    });
                }

                @Override
                public void onFailure(int reasonCode) {
                    runOnUiThread(() -> {
                        String reason = getFailureReason(reasonCode);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.discovery_failed_reason, reason),
                                Toast.LENGTH_SHORT).show();
                        tvEmptyDevices.setText(getString(R.string.discovery_failed_hint, reason));
                    });
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during discoverPeers", e);
            Toast.makeText(this, R.string.discovery_permission_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmAndConnect(WifiP2pDevice device) {
        String deviceName = (device.deviceName != null && !device.deviceName.isEmpty())
                ? device.deviceName : device.deviceAddress;

        new AlertDialog.Builder(this)
                .setTitle(R.string.connect_dialog_title)
                .setMessage(getString(R.string.connect_dialog_message, deviceName))
                .setPositiveButton(R.string.connect, (dialog, which) -> connectToDevice(device))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void connectToDevice(WifiP2pDevice device) {
        if (wifiP2pManager == null || channel == null) return;

        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.deviceAddress;
        config.wps.setup = WpsInfo.PBC;

        runOnUiThread(() -> {
            tvConnectionStatus.setText(R.string.connecting);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_discovering));
        });

        try {
            wifiP2pManager.connect(channel, config, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,
                                getString(R.string.connection_initiated, device.deviceName),
                                Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(int reasonCode) {
                    runOnUiThread(() -> {
                        String reason = getFailureReason(reasonCode);
                        Toast.makeText(MainActivity.this,
                                getString(R.string.connection_failed, reason),
                                Toast.LENGTH_SHORT).show();
                        tvConnectionStatus.setText(R.string.disconnected);
                        tvConnectionStatus.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.status_disconnected));
                    });
                }
            });
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during connect", e);
            Toast.makeText(this, R.string.connect_permission_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void disconnectFromPeer() {
        if (wifiP2pManager != null && channel != null) {
            wifiP2pManager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    onDisconnected();
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.disconnected_success, Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onFailure(int reasonCode) {
                    Log.w(TAG, "removeGroup failed with code: " + reasonCode);
                    onDisconnected();
                }
            });
        }
        if (voiceCommunication != null) {
            voiceCommunication.stopConnection();
        }
    }

    // ==================== WifiDirectEventListener Callbacks ====================

    @Override
    public void onWifiDirectEnabled(boolean isEnabled) {
        runOnUiThread(() -> {
            if (!isEnabled) {
                tvConnectionStatus.setText(R.string.wifi_disabled);
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
                Toast.makeText(this, R.string.wifi_enable_request, Toast.LENGTH_LONG).show();
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
                tvEmptyDevices.setText(R.string.no_devices_found);
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
                tvConnectionStatus.setText(R.string.connected);
                tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));

                btnDisconnect.setEnabled(true);
                updateSpeakButtonState();

                if (receivedDataHistory.length() == 0) {
                    tvReceivedData.setText(R.string.connected_waiting);
                }

                if (wifiP2pInfo.isGroupOwner) {
                    tvRoleDetails.setText(getString(R.string.role_group_owner_ip,
                            wifiP2pInfo.groupOwnerAddress != null ? wifiP2pInfo.groupOwnerAddress.getHostAddress() : "N/A"));
                    // Host the offline voice link
                    if (voiceCommunication != null) {
                        voiceCommunication.startConnection(true, wifiP2pInfo.groupOwnerAddress);
                    }
                } else {
                    tvRoleDetails.setText(getString(R.string.role_client_ip,
                            wifiP2pInfo.groupOwnerAddress != null ? wifiP2pInfo.groupOwnerAddress.getHostAddress() : "N/A"));
                    // Join the host's offline voice link
                    if (voiceCommunication != null) {
                        voiceCommunication.startConnection(false, wifiP2pInfo.groupOwnerAddress);
                    }
                }
            });
        }
    }

    @Override
    public void onDisconnected() {
        isWifiConnected = false;
        runOnUiThread(() -> {
            tvConnectionStatus.setText(R.string.disconnected);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            tvRoleDetails.setText(R.string.role_not_connected);
            btnDisconnect.setEnabled(false);
            updateSpeakButtonState();
            tvSpeakingStatus.setVisibility(View.GONE);
            tvReceivedData.setText(R.string.disconnected_reconnect);
        });

        if (voiceCommunication != null) {
            voiceCommunication.stopConnection();
        }
    }

    @Override
    public void onThisDeviceChanged(WifiP2pDevice device) {
        runOnUiThread(() -> {
            if (device != null) {
                String name = (device.deviceName != null && !device.deviceName.isEmpty())
                        ? device.deviceName : "Unknown";
                tvMyDeviceDetails.setText(getString(R.string.my_device_details, name, device.deviceAddress));
            }
        });
    }

    // ==================== VoiceCommunication.Listener ====================

    @Override
    public void onConnectionEstablished(boolean isServer, String remoteAddress) {
        isWifiConnected = true;
        runOnUiThread(() -> {
            tvConnectionStatus.setText(R.string.connected_socket_active);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            updateSpeakButtonState();
        });
    }

    @Override
    public void onConnectionLost() {
        isWifiConnected = false;
        runOnUiThread(() -> updateSpeakButtonState());
    }

    @Override
    public void onPartialResult(String hypothesis) {
        runOnUiThread(() -> {
            if (isRecording && !hypothesis.isEmpty()) {
                tvSpeakingStatus.setVisibility(View.VISIBLE);
                tvSpeakingStatus.setText(getString(R.string.listening_partial, hypothesis));
            }
        });
    }

    @Override
    public void onMessageSent(String text) {
        runOnUiThread(() -> {
            tvSpeakingStatus.setVisibility(View.VISIBLE);
            tvSpeakingStatus.setText(getString(R.string.message_sent, text));
        });
    }

    @Override
    public void onMessageReceived(String text) {
        runOnUiThread(() -> appendReceivedData(text));
    }

    @Override
    public void onTtsStarted(String text) {
        runOnUiThread(() -> {
            tvSpeakingStatus.setVisibility(View.VISIBLE);
            tvSpeakingStatus.setText(getString(R.string.speaking_text, text));
        });
    }

    @Override
    public void onError(String errorMessage) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show());
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
        if (voiceCommunication != null) {
            voiceCommunication.release();
            voiceCommunication = null;
        }
        if (wifiP2pManager != null && channel != null) {
            try {
                wifiP2pManager.removeGroup(channel, null);
            } catch (Exception ignored) {}
        }
    }
}