package com.example.itantra_sih;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.itantra_sih.speech.tts.OfflineTTS;
import com.example.itantra_sih.speech.tts.PiperEngine;
import com.example.itantra_sih.speech.tts.TTSManager;

public class MainActivity extends AppCompatActivity {
    private TTSManager ttsManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ttsManager = new TTSManager(new PiperEngine(this));

        Button speakButton = findViewById(R.id.speakButton);
        speakButton.setOnClickListener(v -> {
            ttsManager.speak("Define the data used between modules");
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