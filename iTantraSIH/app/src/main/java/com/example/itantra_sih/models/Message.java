package com.example.itantra_sih.models;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Common message model exchanged between the STT, TTS and Wi-Fi modules.
 *
 * Sprint 1 of the agile spec requires a shared Message class so that:
 *   - senderId distinguishes who sent a message
 *   - timestamp provides ordering
 *   - language lets messages be routed to the right STT/TTS model
 *   - toJson/fromJson give a stable wire format for the Wi-Fi transport
 */
public class Message {

    public static final String DEFAULT_LANGUAGE = "en";

    private final String text;
    private final String senderId;
    private final long timestamp;
    private final String language;

    public Message(String text, String senderId, long timestamp, String language) {
        this.text = text == null ? "" : text;
        this.senderId = senderId == null ? "" : senderId;
        this.timestamp = timestamp;
        this.language = language == null || language.isEmpty() ? DEFAULT_LANGUAGE : language;
    }

    public Message(String text, String senderId) {
        this(text, senderId, System.currentTimeMillis(), DEFAULT_LANGUAGE);
    }

    public String getText() {
        return text;
    }

    public String getSenderId() {
        return senderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getLanguage() {
        return language;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("text", text);
            json.put("senderId", senderId);
            json.put("timestamp", timestamp);
            json.put("language", language);
        } catch (JSONException e) {
            // Fields above are never null, so this cannot fail in practice.
        }
        return json;
    }

    public String toJsonString() {
        return toJson().toString();
    }

    public static Message fromJson(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) return null;
        try {
            return fromJson(new JSONObject(jsonString));
        } catch (JSONException e) {
            return null;
        }
    }

    public static Message fromJson(JSONObject json) {
        if (json == null) return null;
        String text = json.optString("text", "");
        String senderId = json.optString("senderId", "");
        long timestamp = json.optLong("timestamp", System.currentTimeMillis());
        String language = json.optString("language", DEFAULT_LANGUAGE);
        return new Message(text, senderId, timestamp, language);
    }
}