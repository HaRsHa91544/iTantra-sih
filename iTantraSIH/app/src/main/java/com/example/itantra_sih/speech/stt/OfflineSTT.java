package com.example.itantra_sih.speech.stt;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Robust Offline Speech-to-Text implementation using Vosk's SpeechService.
 * Supports dynamic language model loading, background unpacking, and RAM management.
 */
public class OfflineSTT implements STTEngine, RecognitionListener {

    private static final String TAG = "OfflineSTT";
    private static final float SAMPLE_RATE = 16000.0f;

    private final ExecutorService modelExecutor = Executors.newSingleThreadExecutor();

    private Model model;
    private Recognizer recognizer;
    private SpeechService speechService;
    private OnResultListener resultListener;
    private boolean isReady = false;

    @Override
    public void init(Context context, OnInitListener listener) {
        // Default to English
        loadLanguage(context, "English", listener);
    }

    @Override
    public void loadLanguage(Context context, String languageName, OnInitListener listener) {
        unloadModel();
        isReady = false;

        String assetDir = getAssetDirForLanguage(context, languageName);
        Log.d(TAG, "Loading language: " + languageName + " from asset directory: " + assetDir);

        modelExecutor.execute(() -> {
            try {
                // Internal storage location: context.getFilesDir()/vosk-models/<assetDir>
                File modelsRoot = new File(context.getFilesDir(), "vosk-models");
                File modelFolder = new File(modelsRoot, assetDir);

                // Check if already unpacked and valid (final.mdl must exist and be > 1MB)
                File mdlFile = new File(modelFolder, "am/final.mdl");
                if (!mdlFile.exists()) {
                    mdlFile = new File(modelFolder, "final.mdl");
                }

                if (!mdlFile.exists() || mdlFile.length() < 1000000) {
                    Log.d(TAG, "Unpacking " + assetDir + " from assets to " + modelFolder.getAbsolutePath());
                    if (modelFolder.exists()) {
                        deleteRecursive(modelFolder);
                    }
                    if (!modelFolder.mkdirs() && !modelFolder.exists()) {
                        throw new IOException("Failed to create directory: " + modelFolder.getAbsolutePath());
                    }
                    copyAssetFolder(context.getAssets(), assetDir, modelFolder);
                } else {
                    Log.d(TAG, "Model " + languageName + " already unpacked on disk. Loading directly...");
                }

                // Verify unpacked model files
                File verifiedMdl = new File(modelFolder, "am/final.mdl");
                if (!verifiedMdl.exists()) {
                    verifiedMdl = new File(modelFolder, "final.mdl");
                }
                if (!verifiedMdl.exists()) {
                    throw new IOException("Model acoustic file final.mdl not found in " + modelFolder.getAbsolutePath());
                }

                // Load Kaldi/Vosk Model into RAM
                Model loadedModel = new Model(modelFolder.getAbsolutePath());
                synchronized (this) {
                    this.model = loadedModel;
                    this.isReady = true;
                }
                Log.d(TAG, "Model " + languageName + " loaded successfully into RAM.");
                if (listener != null) {
                    listener.onReady();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load Vosk model for " + languageName, e);
                synchronized (this) {
                    this.isReady = false;
                }
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    private static void copyAssetFolder(AssetManager assetManager, String fromAssetPath, File toDir) throws IOException {
        String[] files = assetManager.list(fromAssetPath);
        if (files == null || files.length == 0) {
            // It is a file or empty: try opening as stream
            try (InputStream in = assetManager.open(fromAssetPath)) {
                File parent = toDir.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                try (OutputStream out = new FileOutputStream(toDir)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                    out.flush();
                }
            } catch (IOException e) {
                // If it was an empty directory, create it
                if (!toDir.exists()) {
                    toDir.mkdirs();
                }
            }
        } else {
            if (!toDir.exists() && !toDir.mkdirs()) {
                throw new IOException("Failed to create directory " + toDir.getAbsolutePath());
            }
            for (String file : files) {
                String subFrom = fromAssetPath.isEmpty() ? file : fromAssetPath + "/" + file;
                File subTo = new File(toDir, file);
                copyAssetFolder(assetManager, subFrom, subTo);
            }
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    @Override
    public synchronized void unloadModel() {
        if (speechService != null) {
            try {
                speechService.stop();
                speechService.shutdown();
            } catch (Exception ignored) {}
            speechService = null;
        }
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception ignored) {}
            recognizer = null;
        }
        if (model != null) {
            try {
                model.close();
            } catch (Exception ignored) {}
            model = null;
        }
        isReady = false;
        Log.d(TAG, "Previous model unloaded from RAM.");
    }

    public static String getAssetDirForLanguage(Context context, String lang) {
        if (lang == null) return "model-en-us";
        switch (lang.toLowerCase().trim()) {
            case "telugu":
                return "model-te";
            case "hindi":
                return "model-hi";
            case "kannada":
                return assetExists(context, "model-kn") ? "model-kn" : "model-te";
            case "tamil":
                return assetExists(context, "model-ta") ? "model-ta" : "model-te";
            case "english":
            default:
                return "model-en-us";
        }
    }

    private static boolean assetExists(Context context, String assetName) {
        try {
            String[] list = context.getAssets().list("");
            if (list != null) {
                for (String s : list) {
                    if (s.equals(assetName)) return true;
                }
            }
        } catch (IOException ignored) {}
        return false;
    }

    @Override
    public void setOnResultListener(OnResultListener listener) {
        this.resultListener = listener;
    }

    @Override
    public synchronized void start() {
        if (!isReady || model == null) {
            Log.e(TAG, "Cannot start recognition: Model not ready in RAM.");
            if (resultListener != null) {
                resultListener.onError(new IllegalStateException("Model not ready in RAM"));
            }
            return;
        }

        try {
            if (speechService != null) {
                speechService.stop();
                speechService.shutdown();
                speechService = null;
            }
            if (recognizer != null) {
                recognizer.close();
                recognizer = null;
            }

            recognizer = new Recognizer(model, SAMPLE_RATE);
            speechService = new SpeechService(recognizer, SAMPLE_RATE);
            speechService.startListening(this);
            Log.d(TAG, "SpeechService started listening.");
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize Recognizer/SpeechService", e);
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
    public synchronized void stop() {
        if (speechService != null) {
            try {
                speechService.stop();
            } catch (Exception ignored) {}
            speechService = null;
            Log.d(TAG, "SpeechService stopped.");
        }
    }

    @Override
    public synchronized void destroy() {
        unloadModel();
        modelExecutor.shutdown();
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