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
    public final String text;
    public final String direction;
    public final String speech;
    
    // DITAMBAHKAN: Untuk mencocokkan dengan SystemActionInjector
    public final String textToType;   
    public final String globalAction; 
    
    public final String rawJson;

    public ActionCommandDto(String jsonString) throws JSONException {
        this.rawJson = jsonString;
        JSONObject json = new JSONObject(jsonString);
        
        this.action = json.optString("action", "unknown").toLowerCase();
        this.x = json.optInt("x", -1);
        this.y = json.optInt("y", -1);
        this.text = json.optString("text", "");
        this.direction = json.optString("direction", "");
        this.speech = json.optString("speech", "");
        
        // DITAMBAHKAN: Otomatis mengambil dari "textToType" atau "text"
        this.textToType = json.optString("textToType", this.text); 
        // DITAMBAHKAN: Otomatis mengambil dari "globalAction" atau "action"
        this.globalAction = json.optString("globalAction", this.action); 
    }
}
