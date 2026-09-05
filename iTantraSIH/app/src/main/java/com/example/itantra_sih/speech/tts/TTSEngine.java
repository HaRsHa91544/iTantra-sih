package com.example.itantra_sih.speech.tts;

public interface TTSEngine {
    void speak(String text);
    void stop();

    void release();
}