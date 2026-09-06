package com.example.itantra_sih;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Spinner;
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

import com.example.itantra_sih.nearby.NearbyConnectionsManager;
import com.example.itantra_sih.nearby.NearbyDevice;
import com.example.itantra_sih.nearby.NearbyDeviceAdapter;
import com.example.itantra_sih.speech.stt.OfflineSTT;
import com.example.itantra_sih.speech.stt.STTEngine;
import com.example.itantra_sih.speech.tts.TTSManager;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements
        NearbyConnectionsManager.NearbyEventListener {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 1001;
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 2001;

    // UI elements - Connection
    private TextView tvConnectionStatus;
    private TextView tvMyDeviceDetails;
    private TextView tvRoleDetails;
    private TextView tvEmptyDevices;
    private Button btnAdvertise;
    private Button btnDiscover;
    private Button btnDisconnect;
    private ListView lvDevices;

    // UI elements - Language & Received Data
    private Spinner spLanguage;
    private TextView tvModelStatus;
    private ScrollView scrollReceivedData;
    private TextView tvReceivedData;
    private TextView tvSpeakingStatus;
    private Button btnToggleSpeaking;
    private Button btnPlayReceivedAudio;

    // Text-to-Speech Engine
    private TTSManager ttsManager;

    // Supported Languages
    private final String[] supportedLanguages = {"English", "Telugu", "Hindi", "Kannada", "Tamil"};
    private int currentLanguageIndex = 0;
    private boolean isUserActionSpinner = false;

    // Speech-to-Text Engine state
    private STTEngine sttEngine;
    private boolean isRecording = false;
    private boolean isModelReady = false;
    private final StringBuilder receivedDataHistory = new StringBuilder();

    // Google Nearby Connections Manager
    private NearbyConnectionsManager nearbyManager;
    private final List<NearbyDevice> discoveredDevices = new ArrayList<>();
    private NearbyDeviceAdapter deviceAdapter;
    private String localDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        localDeviceName = Build.MANUFACTURER + " " + Build.MODEL;

        ttsManager = new TTSManager(this);

        initViews();
        setupLanguageDropdown();
        initNearbyConnections();
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
        btnAdvertise = findViewById(R.id.btnAdvertise);
        btnDiscover = findViewById(R.id.btnDiscover);
        btnDisconnect = findViewById(R.id.btnDisconnect);
        lvDevices = findViewById(R.id.lvDevices);

        tvMyDeviceDetails.setText("My Device: " + localDeviceName);

        // Language & Received Data Views
        spLanguage = findViewById(R.id.spLanguage);
        tvModelStatus = findViewById(R.id.tvModelStatus);
        scrollReceivedData = findViewById(R.id.scrollReceivedData);
        tvReceivedData = findViewById(R.id.tvReceivedData);
        tvSpeakingStatus = findViewById(R.id.tvSpeakingStatus);
        btnToggleSpeaking = findViewById(R.id.btnToggleSpeaking);
        btnPlayReceivedAudio = findViewById(R.id.btnPlayReceivedAudio);

        deviceAdapter = new NearbyDeviceAdapter(this, discoveredDevices);
        lvDevices.setAdapter(deviceAdapter);

        updateSpeakButtonState();
    }

    private void setupLanguageDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                supportedLanguages
        );
        spLanguage.setAdapter(adapter);
        spLanguage.setSelection(0, false); // Default English

        spLanguage.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isUserActionSpinner) {
                    isUserActionSpinner = true;
                    return;
                }

                if (position != currentLanguageIndex) {
                    String selectedLang = supportedLanguages[position];

                    // Display popup asking user to accept/decline model import
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Import " + selectedLang + " Model")
                            .setMessage("Do you want to import and load the " + selectedLang + " language model into RAM?")
                            .setPositiveButton("Accept", (dialog, which) -> {
                                currentLanguageIndex = position;
                                loadLanguageModel(selectedLang);
                            })
                            .setNegativeButton("Decline", (dialog, which) -> {
                                // Revert to previously active language
                                isUserActionSpinner = false;
                                spLanguage.setSelection(currentLanguageIndex);
                            })
                            .setCancelable(false)
                            .show();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadLanguageModel(String language) {
        runOnUiThread(() -> {
            isModelReady = false;
            tvModelStatus.setText("Model: Loading " + language + " into RAM...");
            updateSpeakButtonState();
            Toast.makeText(this, "Loading " + language + " model into RAM, please wait...", Toast.LENGTH_SHORT).show();
        });

        sttEngine.loadLanguage(this, language, new STTEngine.OnInitListener() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    isModelReady = true;
                    tvModelStatus.setText("Model: " + language + " (Ready in RAM)");
                    updateSpeakButtonState();
                    Toast.makeText(MainActivity.this, language + " model loaded into RAM!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    tvModelStatus.setText("Model: Failed (" + language + ")");
                    Toast.makeText(MainActivity.this, "Failed to load " + language + " model: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void initNearbyConnections() {
        nearbyManager = new NearbyConnectionsManager(this, this);
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

                        // Send speech text to peer over Google Nearby Connections link
                        if (nearbyManager != null && nearbyManager.isConnected()) {
                            nearbyManager.sendPayload(spoken);
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

        // Load default English model initially
        loadLanguageModel(supportedLanguages[0]);
    }

    private void setupListeners() {
        btnAdvertise.setOnClickListener(v -> {
            checkAndRequestPermissions();
            nearbyManager.startAdvertising(localDeviceName);
        });

        btnDiscover.setOnClickListener(v -> {
            checkAndRequestPermissions();
            discoveredDevices.clear();
            deviceAdapter.notifyDataSetChanged();
            tvEmptyDevices.setVisibility(View.VISIBLE);
            tvEmptyDevices.setText("Scanning for nearby BLE beacons (<1s)...");
            nearbyManager.startDiscovery();
        });

        btnDisconnect.setOnClickListener(v -> {
            nearbyManager.disconnect();
            onDisconnected("local");
        });

        lvDevices.setOnItemClickListener((parent, view, position, id) -> {
            NearbyDevice device = discoveredDevices.get(position);
            confirmAndConnect(device);
        });

        // Speak Button
        btnToggleSpeaking.setOnClickListener(v -> {
            if (!nearbyManager.isConnected()) {
                Toast.makeText(this, "Connect to another device first", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!isModelReady) {
                Toast.makeText(this, "Speech model is still loading into RAM, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isRecording) {
                stopSpeaking();
            } else {
                checkMicrophonePermissionAndStart();
            }
        });

        // Listen / Play Received Audio via English TTS
        if (btnPlayReceivedAudio != null) {
            btnPlayReceivedAudio.setOnClickListener(v -> {
                String textToSpeak = tvReceivedData.getText().toString().trim();
                if (textToSpeak.isEmpty()
                        || textToSpeak.startsWith("Waiting for data")
                        || textToSpeak.startsWith("Disconnected")
                        || textToSpeak.startsWith("Connected! Waiting")) {
                    Toast.makeText(this, "No received speech text to play yet", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (ttsManager != null) {
                    ttsManager.speak(textToSpeak);
                    Toast.makeText(this, "Speaking text aloud...", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void confirmAndConnect(NearbyDevice device) {
        new AlertDialog.Builder(this)
                .setTitle("Connect to Device")
                .setMessage("Do you want to connect to " + device.getDeviceName() + "?")
                .setPositiveButton("Connect", (dialog, which) -> {
                    device.setStatus("Connecting...");
                    deviceAdapter.notifyDataSetChanged();
                    nearbyManager.requestConnection(localDeviceName, device.getEndpointId());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSpeakButtonState() {
        boolean connected = nearbyManager != null && nearbyManager.isConnected();
        if (!connected) {
            btnToggleSpeaking.setEnabled(false);
            btnToggleSpeaking.setText("Start speaking");
            if (isRecording) {
                stopSpeaking();
            }
        } else if (!isModelReady) {
            btnToggleSpeaking.setEnabled(false);
            btnToggleSpeaking.setText("Loading model...");
        } else {
            btnToggleSpeaking.setEnabled(true);
            btnToggleSpeaking.setText(isRecording ? "Stop Speaking" : "Start speaking");
        }
    }

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
     * Runtime permissions for Nearby Connections (BLE & Wi-Fi) based on Android version.
     */
    private void checkAndRequestPermissions() {
        List<String> permissions = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API 31+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        if (!permissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    this,
                    permissions.toArray(new String[0]),
                    PERMISSIONS_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int res : grantResults) {
                if (res != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted for Nearby Connections", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth & Wi-Fi permissions required for discovery", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeaking();
            } else {
                Toast.makeText(this, "Microphone permission required to record audio", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ==================== NearbyEventListener Callbacks ====================

    @Override
    public void onEndpointFound(String endpointId, String endpointName) {
        runOnUiThread(() -> {
            boolean exists = false;
            for (NearbyDevice d : discoveredDevices) {
                if (d.getEndpointId().equals(endpointId)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                discoveredDevices.add(new NearbyDevice(endpointId, endpointName, "Available"));
                deviceAdapter.notifyDataSetChanged();
                tvEmptyDevices.setVisibility(View.GONE);
                lvDevices.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onEndpointLost(String endpointId) {
        runOnUiThread(() -> {
            discoveredDevices.removeIf(d -> d.getEndpointId().equals(endpointId));
            deviceAdapter.notifyDataSetChanged();
            if (discoveredDevices.isEmpty()) {
                tvEmptyDevices.setText("No nearby devices found.");
                tvEmptyDevices.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onConnectionInitiated(String endpointId, String endpointName) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Connecting to " + endpointName + "...");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_discovering));
        });
    }

    @Override
    public void onConnected(String endpointId, String endpointName) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Connected to " + endpointName);
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_connected));
            tvRoleDetails.setText("Peer: " + endpointName + " (High-Speed Link)");

            btnDisconnect.setEnabled(true);
            btnAdvertise.setEnabled(false);
            btnDiscover.setEnabled(false);
            updateSpeakButtonState();

            if (receivedDataHistory.length() == 0) {
                tvReceivedData.setText("Connected! Waiting for peer speech data...\n");
            }
            Toast.makeText(this, "Connected to " + endpointName + "!", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDisconnected(String endpointId) {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Disconnected");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_disconnected));
            tvRoleDetails.setText("Connection: Google Nearby (Fast BLE + Wi-Fi)");

            btnDisconnect.setEnabled(false);
            btnAdvertise.setEnabled(true);
            btnDiscover.setEnabled(true);
            updateSpeakButtonState();

            tvSpeakingStatus.setVisibility(View.GONE);
            tvReceivedData.setText("Disconnected. Connect to another device to communicate.\n");
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onMessageReceived(String message) {
        runOnUiThread(() -> {
            appendReceivedData(message);
            if (ttsManager != null) {
                ttsManager.speak(message);
            }
        });
    }

    @Override
    public void onError(String errorMessage) {
        runOnUiThread(() -> {
            Log.e(TAG, "Nearby Error: " + errorMessage);
            Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onAdvertisingStarted() {
        runOnUiThread(() -> {
            tvConnectionStatus.setText("Advertising... (Discoverable)");
            tvConnectionStatus.setTextColor(ContextCompat.getColor(this, R.color.status_discovering));
            Toast.makeText(this, "Device is now discoverable via BLE beacons", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onDiscoveryStarted() {
        runOnUiThread(() -> {
            Toast.makeText(this, "Scanning for nearby devices via BLE...", Toast.LENGTH_SHORT).show();
        });
    }

    private void appendReceivedData(String message) {
        if (receivedDataHistory.length() > 0) {
            receivedDataHistory.append("\n\n");
        }
        receivedDataHistory.append(message);
        tvReceivedData.setText(receivedDataHistory.toString());
        scrollReceivedData.post(() -> scrollReceivedData.fullScroll(View.FOCUS_DOWN));
    }

    // ==================== Lifecycle ====================

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
        if (ttsManager != null) {
            ttsManager.release();
            ttsManager = null;
        }
        if (nearbyManager != null) {
            nearbyManager.disconnect();
        }
    }
}