package com.example.itantra_sih;

import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.itantra_sih.speech.tts.OfflineTTS;

public class MainActivity extends AppCompatActivity {
    private OfflineTTS tts;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tts = new OfflineTTS(this);
        Button speakButton = findViewById(R.id.speakButton);
        speakButton.setOnClickListener(v -> {
            tts.speak("Hello Harsha");
        });
    }
}