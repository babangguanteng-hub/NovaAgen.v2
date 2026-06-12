package com.novaagent.app.data.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ActionCommandDto {
    public final String action;
    public final int x;
    public final int y;
    public final String text;
    public final String direction;
    public final String speech;
    public final String textToType;   
    public final String globalAction; 
    public final String rawJson;

    public ActionCommandDto(String rawString) throws JSONException {
        // [ANTI-BUG 3]: Membersihkan balasan LLM dari markdown (```json ... ```)
        String cleanJson = rawString;
        int startIndex = cleanJson.indexOf("{");
        int endIndex = cleanJson.lastIndexOf("}");
        if (startIndex >= 0 && endIndex >= 0 && endIndex > startIndex) {
            cleanJson = cleanJson.substring(startIndex, endIndex + 1);
        }

        this.rawJson = cleanJson;
        JSONObject json = new JSONObject(cleanJson);
        
        this.action = json.optString("action", "unknown").toLowerCase();
        this.x = json.optInt("x", -1);
        this.y = json.optInt("y", -1);
        this.text = json.optString("text", "");
        this.direction = json.optString("direction", "");
        this.speech = json.optString("speech", "");
        
        this.textToType = json.optString("textToType", this.text); 
        this.globalAction = json.optString("globalAction", this.action); 
    }
}
