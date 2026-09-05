package com.example.itantra_sih.speech.tts;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

/**
 * TinyTTS engine implementing the app's TTSEngine interface.
 *
 * Loads the 4 ONNX models once, synthesizes text on a background thread,
 * and plays the resulting waveform through AudioTrack. Fully offline.
 */
public class TinyTTS implements TTSEngine {
    private static final String TAG = "TinyTTS";
    private static final Object LOCK = new Object();

    private final Context context;
    private TinyTTSPipeline pipeline;
    private AudioTrack currentTrack;
    private boolean released = false;
    private volatile Thread worker;

    public TinyTTS(Context context) {
        this.context = context.getApplicationContext();
        loadPipelineAsync();
    }

    private void loadPipelineAsync() {
        new Thread(() -> {
            try {
                synchronized (LOCK) {
                    if (released) return;
                    pipeline = new TinyTTSPipeline(context);
                }
                Log.i(TAG, "TinyTTS models loaded");
            } catch (Exception e) {
                Log.e(TAG, "TinyTTS model load failed", e);
            }
        }).start();
    }

    @Override
    public void speak(String text) {
        if (text == null || text.isEmpty()) return;
        synchronized (LOCK) {
            if (pipeline == null) {
                Log.w(TAG, "speak() before models ready, ignoring");
                return;
            }
        }
        worker = new Thread(() -> {
            try {
                float[] audio;
                synchronized (LOCK) {
                    if (released) return;
                    audio = pipeline.synthesize(text);
                }
                if (audio == null || audio.length == 0) {
                    Log.w(TAG, "no audio produced");
                    return;
                }
                play(audio, pipeline.getSampleRate());
            } catch (Exception e) {
                Log.e(TAG, "synthesis failed", e);
            }
        }, "TinyTTS-synth");
        worker.start();
    }

    private void play(float[] samples, int sampleRate) {
        float peak = 0f;
        for (float s : samples) {
            peak = Math.max(peak, Math.abs(s));
        }
        float gain = (peak > 0f) ? Math.min(0.95f / peak, 8f) : 1f;

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
        track.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
        track.play();
        long durationMs = (long) (samples.length / (double) sampleRate * 1000);
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < durationMs) {
            synchronized (LOCK) {
                if (currentTrack == null) break; // stopped
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
            if (pipeline != null) {
                pipeline.close();
                pipeline = null;
            }
        }
    }
}