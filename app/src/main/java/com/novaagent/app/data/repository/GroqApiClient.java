package com.novaagent.app.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
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
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    // Tetap menggunakan Llama-3.1-70b
    private static final String MODEL_NAME = "llama-3.1-70b-versatile"; 
    
    private final OkHttpClient client;
    private final Context context;

    public interface GroqCallback {
        void onSuccess(String jsonResponse);
        void onError(String errorMessage);
    }

    public GroqApiClient(Context context) {
        this.context = context;
        client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public void sendPrompt(String systemPrompt, String userPrompt, GroqCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences("nova_config", Context.MODE_PRIVATE);
        String apiKey = prefs.getString("groq_api_key", "").trim();

        if (apiKey.isEmpty()) {
            callback.onError("API Key kosong!");
            return;
        }

        try {
            JSONObject payload = new JSONObject();
            payload.put("model", MODEL_NAME);
            
            // [ANTI HTTP 400]: Kita hapus 'response_format' strict JSON yang sering ditolak server.
            // Penyaring Regex di ActionCommandDto kita sudah cukup untuk menangkap JSON-nya.

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

            RequestBody body = RequestBody.create(payload.toString(), MediaType.parse("application/json; charset=utf-8"));
            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("Koneksi Internet Terputus!");
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        // [DETEKTIF ERROR]: Menarik pesan asli dari dalam server Groq
                        String errorBody = response.body() != null ? response.body().string() : "";
                        String exactError = "HTTP " + response.code();
                        try {
                            JSONObject errObj = new JSONObject(errorBody);
                            exactError = errObj.getJSONObject("error").getString("message");
                        } catch (Exception ignored) {}
                        
                        callback.onError(exactError);
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
                    } catch (Exception e) {
                        callback.onError("Gagal baca JSON AI");
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Internal Sistem Request");
        }
    }
}
