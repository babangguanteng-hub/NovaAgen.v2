package com.novaagent.app.data.model;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Merepresentasikan perintah fisik dari agen AI.
 * Dikonversi dari JSON output Groq Llama-3.
 */
public class ActionCommandDto {
    public final String action;
    public final int x;
    public final int y;
    public final String text;       // Untuk fitur mengetik
    public final String direction;  // Untuk fitur swipe/scroll
    public final String speech;     // DITAMBAHKAN: Untuk fitur bicara/TTS
    public final String rawJson;

    public ActionCommandDto(String jsonString) throws JSONException {
        this.rawJson = jsonString;
        JSONObject json = new JSONObject(jsonString);
        this.action = json.optString("action", "unknown").toLowerCase();
        this.x = json.optInt("x", -1);
        this.y = json.optInt("y", -1);
        this.text = json.optString("text", "");
        this.direction = json.optString("direction", "");
        this.speech = json.optString("speech", ""); // DITAMBAHKAN
    }
}
