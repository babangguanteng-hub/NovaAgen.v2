package com.novaagent.app.data.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Penggabungan hasil antara Accessibility Tree (Struktur UI Native) dan ML Kit OCR (Teks Gambar).
 * Dikonversi menjadi DTO agar mudah dievaluasi oleh SafetyEngine dan LLM Groq.
 */
public class UnifiedScreenContext {
    public final String packageName;
    public final long timestamp;
    public final List<ScreenElement> elements;

    public UnifiedScreenContext(String packageName, JSONArray accessibilityNodes, JSONArray ocrNodes) {
        this.packageName = packageName != null ? packageName : "unknown";
        this.timestamp = System.currentTimeMillis();
        this.elements = new ArrayList<>();
        
        parseJsonArray(accessibilityNodes, "accessibility");
        parseJsonArray(ocrNodes, "ocr");
    }

    private void parseJsonArray(JSONArray array, String source) {
        if (array == null) return;
        for (int i = 0; i < array.length(); i++) {
            try {
                JSONObject obj = array.getJSONObject(i);
                elements.add(new ScreenElement(obj, source));
            } catch (JSONException e) {
                // Abaikan elemen yang error
            }
        }
    }

    /**
     * Helper untuk SafetyPolicyEngine mencari teks di koordinat spesifik.
     */
    public String getTextAtCoordinate(int x, int y) {
        for (ScreenElement el : elements) {
            // Pengecekan hit-box sederhana dengan toleransi 50 pixel
            if (Math.abs(el.cx - x) < 50 && Math.abs(el.cy - y) < 50) {
                return el.text;
            }
        }
        return null;
    }

    public static class ScreenElement {
        public final String text;
        public final int cx;
        public final int cy;
        public final String source;

        public ScreenElement(JSONObject obj, String source) {
            this.text = obj.optString("text", obj.optString("contentDescription", ""));
            this.cx = obj.optInt("cx", -1);
            this.cy = obj.optInt("cy", -1);
            this.source = source;
        }
    }
}
