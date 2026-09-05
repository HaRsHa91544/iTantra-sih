package com.example.itantra_sih.speech.stt;

import android.content.Context;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.StorageService;

import java.io.IOException;

/**
 * Robust Offline Speech-to-Text implementation using Vosk's SpeechService.
 */
public class OfflineSTT implements STTEngine, RecognitionListener {

    private static final String TAG = "OfflineSTT";
    private static final float SAMPLE_RATE = 16000.0f;

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private OnResultListener resultListener;
    private boolean isReady = false;

    @Override
    public void init(Context context, OnInitListener listener) {
        StorageService.unpack(
                context.getApplicationContext(),
                "model-en-us",
                "model",
                (Model loadedModel) -> {
                    this.model = loadedModel;
                    this.isReady = true;
                    Log.d(TAG, "Vosk English model loaded successfully.");
                    if (listener != null) {
                        listener.onReady();
                    }
                },
                (IOException exception) -> {
                    Log.e(TAG, "Failed to unpack/load Vosk model", exception);
                    if (listener != null) {
                        listener.onError(exception);
                    }
                }
        );
    }

    @Override
    public void setOnResultListener(OnResultListener listener) {
        this.resultListener = listener;
    }

    @Override
    public void start() {
        if (!isReady || model == null) {
            Log.w(TAG, "Model is not ready yet.");
            return;
        }

        try {
            if (speechService != null) {
                speechService.stop();
                speechService.shutdown();
                speechService = null;
            }

            recognizer = new Recognizer(model, SAMPLE_RATE);
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(this);
            Log.d(TAG, "SpeechService started listening.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to start SpeechService", e);
            if (resultListener != null) {
                resultListener.onError(e);
            }
        }
    }

    @Override
    public void acceptAudio(byte[] data, int length) {
        // Handled automatically by SpeechService
    }

    @Override
    public void stop() {
        if (speechService != null) {
            speechService.stop();
            speechService = null;
            Log.d(TAG, "SpeechService stopped.");
        }
    }

    @Override
    public void destroy() {
        if (speechService != null) {
            try {
                speechService.stop();
                speechService.shutdown();
            } catch (Exception ignored) {
            }
            speechService = null;
        }
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception ignored) {
            }
            recognizer = null;
        }
        if (model != null) {
            try {
                model.close();
            } catch (Exception ignored) {
            }
            model = null;
        }
        isReady = false;
    }

    @Override
    public void onPartialResult(String hypothesis) {
        String partial = parsePartialJson(hypothesis);
        Log.d(TAG, "onPartialResult: " + partial);
        if (resultListener != null && !partial.isEmpty()) {
            resultListener.onPartialResult(partial);
        }
    }

    @Override
    public void onResult(String hypothesis) {
        String text = parseResultJson(hypothesis);
        Log.d(TAG, "onResult: " + text);
        if (resultListener != null && !text.isEmpty()) {
            resultListener.onFinalResult(text);
        }
    }

    @Override
    public void onFinalResult(String hypothesis) {
        String text = parseResultJson(hypothesis);
        Log.d(TAG, "onFinalResult: " + text);
        if (resultListener != null) {
            resultListener.onFinalResult(text);
        }
    }

    @Override
    public void onError(Exception exception) {
        Log.e(TAG, "Recognition error", exception);
        if (resultListener != null) {
            resultListener.onError(exception);
        }
    }

    @Override
    public void onTimeout() {
        Log.d(TAG, "Recognition timeout");
    }

    private String parseResultJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return "";
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            return jsonObject.optString("text", "").trim();
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing result JSON: " + jsonStr, e);
            return "";
        }
    }

    private String parsePartialJson(String jsonStr) {
        if (jsonStr == null || jsonStr.isEmpty()) return "";
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            return jsonObject.optString("partial", "").trim();
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing partial JSON: " + jsonStr, e);
            return "";
        }
    }
}