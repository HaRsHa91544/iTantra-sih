package com.example.itantra_sih.speech.stt;

import android.content.Context;

/**
 * Interface defining the contract for Speech-To-Text engines in Sprint 3.
 */
public interface STTEngine {

    interface OnInitListener {
        void onReady();
        void onError(Exception e);
    }

    interface OnResultListener {
        void onPartialResult(String hypothesis);
        void onFinalResult(String text);
        void onError(Exception e);
    }

    /**
     * Initialize the STT model asynchronously.
     */
    void init(Context context, OnInitListener listener);

    /**
     * Set the listener for real-time transcription results.
     */
    void setOnResultListener(OnResultListener listener);

    /**
     * Start a new recognition session.
     */
    void start();

    /**
     * Feed raw PCM audio data into the recognition model.
     *
     * @param data   PCM 16-bit Mono audio bytes
     * @param length number of bytes read
     */
    void acceptAudio(byte[] data, int length);

    /**
     * Stop the current recognition session and flush remaining audio.
     */
    void stop();

    /**
     * Release all model and native resources.
     */
    void destroy();
}