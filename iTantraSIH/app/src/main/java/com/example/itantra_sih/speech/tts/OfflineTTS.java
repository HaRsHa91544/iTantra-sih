package com.example.itantra_sih.speech.tts;
import android.content.Context;
import android.speech.tts.TextToSpeech;

public class OfflineTTS implements TTSEngine{
    private TextToSpeech tts;
    private boolean isReady = false;
    public OfflineTTS(Context context){
        tts = new TextToSpeech(context, (status) -> {
            if (status == TextToSpeech.SUCCESS) {
                isReady = true;
            } else {
                isReady = false;
            }
        });
    }
    @Override
    public void speak(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }

        if (isReady) {
            System.out.println("TTS IS READY");
            tts.speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "itantra"
            );
        }
    }
    @Override
    public void stop() {
        tts.stop();
    }

    @Override
    public void release() {
        tts.shutdown();
    }
}
