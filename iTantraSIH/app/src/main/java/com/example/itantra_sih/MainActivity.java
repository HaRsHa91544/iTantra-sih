package com.example.itantra_sih;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.itantra_sih.speech.tts.OfflineTTS;
import com.example.itantra_sih.speech.tts.TTSManager;

public class MainActivity extends AppCompatActivity {
    private TTSManager ttsManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ttsManager = new TTSManager(new OfflineTTS(this));

        Button speakButton = findViewById(R.id.speakButton);
        speakButton.setOnClickListener(v -> {
            ttsManager.speak("Hello Harsha, Reddy, Prudvi, Ganesh, Divya");
        });

        Button stopButton = findViewById(R.id.stopButton);
        stopButton.setOnClickListener(v->{
            ttsManager.stop();
        });
    }

    @Override
    protected void onDestroy() {
        if(ttsManager != null){
            ttsManager.release();
        }
        super.onDestroy();
    }
}