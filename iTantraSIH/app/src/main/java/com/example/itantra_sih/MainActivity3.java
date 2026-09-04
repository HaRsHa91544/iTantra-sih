package com.example.itantra_sih;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.itantra_sih.speech.stt.OfflineSTT;
import com.example.itantra_sih.speech.stt.STTEngine;

public class MainActivity3 extends AppCompatActivity {

    private static final String TAG = "MainActivity3";
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;

    private TextView tvModelStatus;
    private TextView tvTranscribedText;
    private Button btnToggleSpeaking;

    private STTEngine sttEngine;
    private boolean isRecording = false;
    private boolean isModelReady = false;
    private final StringBuilder transcribedTextAccumulator = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main3);

        // Optional: Handle edge-to-edge display (standard in modern Android)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        tvModelStatus = findViewById(R.id.tvModelStatus);
        tvTranscribedText = findViewById(R.id.tvTranscribedText);
        btnToggleSpeaking = findViewById(R.id.btnToggleSpeaking);

        initSTTEngine();

        btnToggleSpeaking.setOnClickListener(v -> {
            if (!isModelReady) {
                Toast.makeText(this, "STT Model is still loading, please wait...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isRecording) {
                stopSpeaking();
            } else {
                checkPermissionAndStart();
            }
        });
    }

    private void initSTTEngine() {
        sttEngine = new OfflineSTT();
        sttEngine.setOnResultListener(new STTEngine.OnResultListener() {
            @Override
            public void onPartialResult(String hypothesis) {
                runOnUiThread(() -> {
                    if (!hypothesis.isEmpty()) {
                        String current = transcribedTextAccumulator.toString();
                        if (current.isEmpty()) {
                            tvTranscribedText.setText(hypothesis + "...");
                        } else {
                            tvTranscribedText.setText(current + " " + hypothesis + "...");
                        }
                    }
                });
            }

            @Override
            public void onFinalResult(String text) {
                runOnUiThread(() -> {
                    if (!text.isEmpty()) {
                        if (transcribedTextAccumulator.length() > 0) {
                            transcribedTextAccumulator.append(" ");
                        }
                        transcribedTextAccumulator.append(text);
                        tvTranscribedText.setText(transcribedTextAccumulator.toString());
                    } else if (transcribedTextAccumulator.length() == 0 && !isRecording) {
                        tvTranscribedText.setText("(No speech detected. Please speak clearly into the mic)");
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "STT Engine error", e);
                runOnUiThread(() -> Toast.makeText(MainActivity3.this, "STT Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        tvModelStatus.setText("Loading offline model...");
        sttEngine.init(this, new STTEngine.OnInitListener() {
            @Override
            public void onReady() {
                runOnUiThread(() -> {
                    isModelReady = true;
                    tvModelStatus.setText("Offline Model Ready (English)");
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    tvModelStatus.setText("Failed to load model: " + e.getMessage());
                });
            }
        });
    }

    private void checkPermissionAndStart() {
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
        transcribedTextAccumulator.setLength(0);
        tvTranscribedText.setText("Listening... Speak now");
        sttEngine.start();
        isRecording = true;
        btnToggleSpeaking.setText("Stop Speaking");
    }

    private void stopSpeaking() {
        isRecording = false;
        sttEngine.stop();
        btnToggleSpeaking.setText("Start speaking");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startSpeaking();
            } else {
                Toast.makeText(this, "Microphone permission is required to record audio", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (isRecording) {
            stopSpeaking();
        }
        if (sttEngine != null) {
            sttEngine.destroy();
            sttEngine = null;
        }
        super.onDestroy();
    }
}