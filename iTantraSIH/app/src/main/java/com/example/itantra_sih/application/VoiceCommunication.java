package com.example.itantra_sih.application;

import com.example.itantra_sih.models.Message;
import com.example.itantra_sih.speech.stt.STTEngine;
import com.example.itantra_sih.speech.tts.TTSEngine;
import com.example.itantra_sih.transport.MessageTransport;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sprint 4 coordinator. Wires the three black boxes together:
 *
 *   Phone A: speak -> STT -> Message -> send(Transport)
 *   Phone B: recv(Transport) -> Message -> text -> TTS -> speaker
 *
 * It deliberately contains NO STT model code, no TTS model code, and no
 * socket code. It only knows three interfaces (STTEngine, TTSEngine,
 * MessageTransport), so any implementation of Sprint 1-3 can be swapped in
 * without touching these layers.
 */
public class VoiceCommunication implements STTEngine.OnResultListener {

    /**
     * UI-facing notifications. All callbacks arrive on the main thread.
     */
    public interface Listener {
        void onPartialResult(String hypothesis);
        void onMessageSent(String text);
        void onMessageReceived(String text);
        void onTtsStarted(String text);
        void onError(String errorMessage);
    }

    private final STTEngine sttEngine;
    private final TTSEngine ttsEngine;
    private final MessageTransport transport;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private Listener listener;

    public VoiceCommunication(STTEngine sttEngine, TTSEngine ttsEngine, MessageTransport transport) {
        this.sttEngine = sttEngine;
        this.ttsEngine = ttsEngine;
        this.transport = transport;

        this.sttEngine.setOnResultListener(this);
        this.transport.setInboundListener(this::handleInboundPayload);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /**
     * Forward manually typed text through the same pipeline as STT results.
     */
    public void sendText(String text) {
        if (text == null || text.trim().isEmpty()) return;
        sendMessage(text);
    }

    /** STT final result -> Message -> send. */
    @Override
    public void onFinalResult(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (!trimmed.isEmpty()) {
            sendMessage(trimmed);
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        if (hypothesis != null && !hypothesis.isEmpty() && listener != null) {
            listener.onPartialResult(hypothesis);
        }
    }

    /** Start a STT listening session, routed through the coordinator. */
    public void startListening() {
        sttEngine.start();
    }

    /** Stop the current STT listening session, routed through the coordinator. */
    public void stopListening() {
        sttEngine.stop();
    }

    @Override
    public void onError(Exception e) {
        if (listener != null) {
            String msg = e == null ? "Unknown STT error" : e.getMessage();
            listener.onError("STT Error: " + msg);
        }
    }

    private void sendMessage(String text) {
        Message message = new Message(text, currentSenderId());
        transport.sendMessagePayload(message.toJsonString());
        if (listener != null) {
            listener.onMessageSent(text);
        }
    }

    /** Received payload -> Message -> text -> TTS. */
    private void handleInboundPayload(String payload) {
        Message message = Message.fromJson(payload);
        String text = message != null ? message.getText() : payload;
        if (text == null || text.trim().isEmpty()) return;

        if (listener != null) {
            listener.onMessageReceived(text);
        }

        String finalText = text;
        executor.execute(() -> {
            if (listener != null) {
                listener.onTtsStarted(finalText);
            }
            ttsEngine.speak(finalText);
        });
    }

    private String currentSenderId() {
        return java.util.UUID.randomUUID().toString();
    }

    public void stopSpeaking() {
        ttsEngine.stop();
    }

    public void release() {
        executor.shutdownNow();
        transport.stopAndShutdown();
        ttsEngine.release();
        sttEngine.destroy();
    }
}