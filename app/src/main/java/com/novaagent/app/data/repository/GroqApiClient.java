package com.novaagent.app.data.repository;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GroqApiClient {
    private static final String TAG = "GroqApiClient";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL_NAME = "llama3-70b-8192";

    private final OkHttpClient client;
    private String apiKey = "";

    public interface GroqCallback {
        void onSuccess(String jsonResponse);
        void onError(String errorMessage);
    }

    public GroqApiClient(Context context) {
        client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .build();
                
        // Mengambil API Key dari penyimpanan lokal
        SharedPreferences prefs = context.getSharedPreferences("nova_config", Context.MODE_PRIVATE);
        this.apiKey = prefs.getString("groq_api_key", "");
    }

    public void sendPrompt(String systemPrompt, String userPrompt, GroqCallback callback) {
        if (apiKey.isEmpty()) {
            callback.onError("API Key belum disetel.");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("model", MODEL_NAME);
            
            JSONObject responseFormat = new JSONObject();
            responseFormat.put("type", "json_object");
            payload.put("response_format", responseFormat);

            JSONArray messages = new JSONArray();
            
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);
            messages.put(userMsg);

            payload.put("messages", messages);

            RequestBody body = RequestBody.create(
                    payload.toString(), 
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            Log.d(TAG, "Mengirim request ke Groq API...");

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Koneksi Error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError("HTTP Error: " + response.code());
                        return;
                    }
                    try {
                        String responseBody = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String content = jsonResponse.getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                        
                        callback.onSuccess(content);
                    } catch (JSONException | NullPointerException e) {
                        callback.onError("Parsing JSON Error");
                    }
                }
            });

        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}
