package com.novaagent.app.data.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ActionCommandDto {
    public final String action;
    public final int x;
    public final int y;
    public final String direction;
    public final String textToType;
    public final String globalAction;
    public final String speech; // Tambahan untuk AI berbicara
    public final String rawJson;

    public ActionCommandDto(String jsonString) throws JSONException {
        this.rawJson = jsonString;
        JSONObject json = new JSONObject(jsonString);
        this.action = json.optString("action", "unknown").toLowerCase();
        this.x = json.optInt("x", -1);
        this.y = json.optInt("y", -1);
        this.direction = json.optString("direction", "");
        this.textToType = json.optString("text_to_type", "");
        this.globalAction = json.optString("global_action", "");
        this.speech = json.optString("speech", "");
    }
}
