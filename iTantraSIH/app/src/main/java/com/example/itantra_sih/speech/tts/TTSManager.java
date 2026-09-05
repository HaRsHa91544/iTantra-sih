package com.example.itantra_sih.speech.tts;

public class TTSManager {
    private TTSEngine tts;
    public TTSManager(TTSEngine tts){
        this.tts = tts;
    }

    public void speak(String text){
        tts.speak(text);
    }

    public void stop(){
        tts.stop();
    }

    public void release(){
        tts.release();
    }
}
