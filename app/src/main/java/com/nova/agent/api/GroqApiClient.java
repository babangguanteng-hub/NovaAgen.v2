package com.nova.agent.api;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * MULTI-AI CLIENT SDK
 * Dikembangkan secara mandiri menggunakan HttpURLConnection murni 
 * agar sangat stabil di HP Android Go, bebas dari error dependensi DEX.
 */
public class GroqApiClient {
    private static final String TAG = "MultiAiClient";

    public interface GroqResponseCallback {
        void onSuccess(String aiResponseText);
        void onError(Throwable throwable);
    }

    public static void requestAiDecision(String provider, String apiKey, String prompt, final GroqResponseCallback callback) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());

        new Thread(() -> {
            try {
                String targetUrl = "";
                String jsonPayload = "";
                String authorizationHeader = "Bearer " + apiKey;
                boolean isGemini = "gemini".equalsIgnoreCase(provider);

                if ("gemini".equalsIgnoreCase(provider)) {
                    // Google Gemini 2.5/1.5 Flash Endpoint
                    targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;
                    JSONObject contentObj = new JSONObject();
                    JSONArray partsArray = new JSONArray();
                    partsArray.put(new JSONObject().put("text", prompt));
                    contentObj.put("parts", partsArray);
                    
                    JSONObject root = new JSONObject();
                    root.put("contents", new JSONArray().put(contentObj));
                    jsonPayload = root.toString();
                } else if ("openrouter".equalsIgnoreCase(provider)) {
                    // OpenRouter Llama 3 Free Endpoint
                    targetUrl = "https://openrouter.ai/api/v1/chat/completions";
                    JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
                    jsonPayload = new JSONObject()
                            .put("model", "meta-llama/llama-3-8b-instruct:free")
                            .put("messages", new JSONArray().put(message))
                            .toString();
                } else {
                    // Default: Groq Llama 3.1 Instant Endpoint
                    targetUrl = "https://api.groq.com/openai/v1/chat/completions";
                    JSONObject message = new JSONObject().put("role", "user").put("content", prompt);
                    jsonPayload = new JSONObject()
                            .put("model", "llama-3.1-8b-instant")
                            .put("messages", new JSONArray().put(message))
                            .toString();
                }

                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                
                if (!isGemini) {
                    conn.setRequestProperty("Authorization", authorizationHeader);
                }

                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) {
                        response.append(line.trim());
                    }

                    String responseString = response.toString();
                    String reply = "";

                    if (isGemini) {
                        // Parsing Respon Gemini
                        JSONObject resJson = new JSONObject(responseString);
                        reply = resJson.getJSONArray("candidates")
                                .getJSONObject(0)
                                .getJSONObject("content")
                                .getJSONArray("parts")
                                .getJSONObject(0)
                                .getString("text");
                    } else {
                        // Parsing Respon OpenAI (Groq / OpenRouter)
                        JSONObject resJson = new JSONObject(responseString);
                        reply = resJson.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                    }

                    final String finalReply = reply;
                    mainHandler.post(() -> callback.onSuccess(finalReply));
                } else {
                    final String errorMsg = "API Error Kode: " + code;
                    mainHandler.post(() -> callback.onError(new Exception(errorMsg)));
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "Gagal koneksi AI: ", e);
                mainHandler.post(() -> callback.onError(new Exception("Koneksi internet bermasalah.")));
            }
        }).start();
    }
}
