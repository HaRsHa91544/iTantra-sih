package com.example.itantra_sih.speech.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKittenModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsMatchaModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsPocketModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsSupertonicModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsZipVoiceModelConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Piper (en_IN-spicor) engine implemented with sherpa-onnx.
 *
 * Model + tokens are read straight from Android assets; only espeak-ng-data
 * is extracted to disk because the espeak phonemizer needs native file access.
 * All generation and playback work is serialized on a single worker thread so
 * the native TTS object is never used concurrently. Fully offline.
 */
public class PiperEngine implements TTSEngine {
    private static final String TAG = "PiperEngine";
    private static final Object LOCK = new Object();

    private static final String MODEL_ASSET = "piper/en_IN-spicor-medium.onnx";
    private static final String TOKENS_ASSET = "piper/tokens.txt";
    private static final String ESPEAK_ASSET = "lespk";

    private final Context context;
    private final ThreadPoolExecutor synthExecutor;
    private OfflineTts tts;
    private AudioTrack currentTrack;
    private volatile boolean stopRequested = false;
    private boolean released = false;

    public PiperEngine(Context context) {
        this.context = context.getApplicationContext();
        this.synthExecutor = new ThreadPoolExecutor(1, 1, 0L,
                TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        loadAsync();
    }

    private void loadAsync() {
        new Thread(() -> {
            try {
                String dataDir = extractEspeakData();
                OfflineTtsVitsModelConfig vits = new OfflineTtsVitsModelConfig(
                        MODEL_ASSET, "", TOKENS_ASSET, dataDir, "",
                        0.667f, 0.8f, 1.0f);
                OfflineTtsModelConfig model = new OfflineTtsModelConfig(
                        vits,
                        new OfflineTtsMatchaModelConfig(),
                        new OfflineTtsKokoroModelConfig(),
                        new OfflineTtsZipVoiceModelConfig(),
                        new OfflineTtsKittenModelConfig(),
                        new OfflineTtsPocketModelConfig(),
                        new OfflineTtsSupertonicModelConfig(),
                        2, true, "cpu");
                OfflineTtsConfig config = new OfflineTtsConfig(model, "", "", 1, 0.2f);
                synchronized (LOCK) {
                    if (released) return;
                    tts = new OfflineTts(context.getAssets(), config);
                }
                Log.i(TAG, "sherpa piper TTS ready");
            } catch (Exception e) {
                Log.e(TAG, "TTS init failed", e);
            }
        }, "PiperEngine-init").start();
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (LOCK) {
            if (released) return;
        }
        synthExecutor.getQueue().clear();
        stopRequested = false;
        synthExecutor.execute(() -> {
            try {
                GeneratedAudio audio;
                synchronized (LOCK) {
                    if (released || tts == null) {
                        Log.w(TAG, "speak() before TTS ready, ignoring");
                        return;
                    }
                    audio = tts.generate(text, 0, 1.0f);
                }
                if (audio == null || audio.getSamples().length == 0) {
                    Log.w(TAG, "no audio produced");
                    return;
                }
                if (stopRequested) {
                    Log.d(TAG, "synthesis cancelled by stop()");
                    return;
                }
                Log.i(TAG, "generated " + audio.getSamples().length + " samples @ "
                        + audio.getSampleRate() + " Hz "
                        + String.format(java.util.Locale.US, "(%.1fs)", audio.getSamples().length
                        / (double) audio.getSampleRate()));
                play(audio.getSamples(), audio.getSampleRate());
            } catch (Exception e) {
                Log.e(TAG, "synthesis failed", e);
            }
        });
    }

    private String extractEspeakData() throws IOException {
        File dst = new File(context.getFilesDir(), ESPEAK_ASSET);
        copyAssets(ESPEAK_ASSET, dst);
        return new File(context.getFilesDir(), "lespk/espeak-ng-data").getAbsolutePath();
    }

    private void copyAssets(String srcRel, File dstFile) throws IOException {
        String[] children = context.getAssets().list(srcRel);
        if (children != null && children.length > 0) {
            if (!dstFile.exists() && !dstFile.mkdirs()) {
                throw new IOException("cannot create dir " + dstFile);
            }
            for (String child : children) {
                copyAssets(srcRel + "/" + child, new File(dstFile, child));
            }
        } else if (children != null) {
            File parent = dstFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("cannot create dir " + parent);
            }
            try (InputStream in = context.getAssets().open(srcRel);
                 FileOutputStream out = new FileOutputStream(dstFile)) {
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
        }
    }

    private void play(float[] samples, int sampleRate) {
        synchronized (LOCK) {
            if (released) return;
        }

        float peak = 0f;
        for (float s : samples) {
            peak = Math.max(peak, Math.abs(s));
        }
        float gain = (peak > 1f) ? 0.95f / peak : 1f;

        short[] pcm = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            float s = Math.max(-1f, Math.min(1f, samples[i] * gain));
            pcm[i] = (short) (s * 32767f);
        }
        int bufferSizeBytes = pcm.length * 2;
        int minBuf = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuf, bufferSizeBytes);
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build())
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build();
        synchronized (LOCK) {
            currentTrack = track;
        }
        int written = track.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
        if (written != pcm.length) {
            Log.e(TAG, "AudioTrack short write: " + written + "/" + pcm.length + " samples");
        }
        if (written <= 0) {
            releaseTrack(track);
            return;
        }
        track.play();
        long durationMs = (long) (samples.length / (double) sampleRate * 1000);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < durationMs) {
            synchronized (LOCK) {
                if (stopRequested || currentTrack == null) break;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                break;
            }
        }
        releaseTrack(track);
    }

    @Override
    public void stop() {
        stopRequested = true;
        synthExecutor.getQueue().clear();
        synchronized (LOCK) {
            if (currentTrack != null) {
                currentTrack.stop();
                currentTrack = null;
            }
        }
    }

    private void releaseTrack(AudioTrack track) {
        try {
            track.stop();
            track.release();
        } catch (Exception ignored) {
        }
        synchronized (LOCK) {
            if (currentTrack == track) currentTrack = null;
        }
    }

    @Override
    public void release() {
        synchronized (LOCK) {
            released = true;
            if (currentTrack != null) {
                currentTrack.stop();
                currentTrack = null;
            }
        }
        synthExecutor.shutdown();
        try {
            synthExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        synchronized (LOCK) {
            if (tts != null) {
                tts.release();
                tts = null;
            }
        }
    }
}