package com.example.itantra_sih.speech.tts;

import android.content.Context;
import android.util.Log;

/**
 * Manages Text-To-Speech execution.
 * Tries PiperEngine (neural Indian-English offline TTS) first.
 * If Piper is still initializing or unavailable, seamlessly falls back to Android system TTS.
 */
public class TTSManager {
    private static final String TAG = "TTSManager";

    private PiperEngine piperEngine;
    private AndroidTTS androidTTS;

    public TTSManager(Context context) {
        // Initialize Android system TTS (instant, lightweight fallback)
        try {
            this.androidTTS = new AndroidTTS(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize AndroidTTS", e);
        }

        // Initialize PiperEngine (neural offline ONNX model)
        try {
            this.piperEngine = new PiperEngine(context);
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize PiperEngine", e);
        }
    }

    public void speak(String text) {
        if (text == null || text.trim().isEmpty()) return;

        String cleanText = text.trim();

        if (piperEngine != null && piperEngine.isReady()) {
            Log.i(TAG, "Speaking via PiperEngine (Offline Neural ONNX): " + cleanText);
            piperEngine.speak(cleanText);
        } else if (androidTTS != null && androidTTS.isReady()) {
            Log.i(TAG, "Speaking via AndroidTTS (Offline System English): " + cleanText);
            androidTTS.speak(cleanText);
        } else if (androidTTS != null) {
            Log.i(TAG, "Queueing speak on AndroidTTS: " + cleanText);
            androidTTS.speak(cleanText);
        } else if (piperEngine != null) {
            Log.i(TAG, "Queueing speak on PiperEngine: " + cleanText);
            piperEngine.speak(cleanText);
        } else {
            Log.w(TAG, "No TTS engine ready to speak: " + cleanText);
        }
    }

    public void stop() {
        if (piperEngine != null) piperEngine.stop();
        if (androidTTS != null) androidTTS.stop();
    }

    public void release() {
        if (piperEngine != null) {
            piperEngine.release();
            piperEngine = null;
        }
        if (androidTTS != null) {
            androidTTS.release();
            androidTTS = null;
        }
    }
}
