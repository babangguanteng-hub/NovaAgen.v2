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

public class GroqApiClient {
    public interface GroqResponseCallback {
        void onSuccess(String aiResponseText, String extractedCommand);
        void onError(Throwable throwable);
    }

    // PROMPT RAHASIA UNTUK KONTROL HP (Inilah yang membuat Nova Pintar!)
    private static final String SYSTEM_PROMPT = 
        "Kamu adalah NOVA AI, asisten virtual Android super cerdas buatan 'Moch Khoirul Azman'. " +
        "Berikan jawaban verbal yang sangat singkat dan natural. " +
        "PENTING: Jika user meminta untuk membuka aplikasi, mencari video, atau melakukan aksi di HP, " +
        "kamu WAJIB menyisipkan KODE PERINTAH di bagian PALING AKHIR jawabanmu (jangan dibacakan). " +
        "Daftar Kode: " +
        "- Buka aplikasi: [CMD:OPEN:nama_aplikasi] " +
        "- Cari Youtube: [CMD:YOUTUBE:kata_kunci] " +
        "- Cari Shopee: [CMD:SHOPEE:kata_kunci] " +
        "- Kembali ke Home: [CMD:HOME] " +
        "Contoh: User: 'Nova tolong carikan lagu peterpan di youtube'. Jawabanmu: 'Baik Bos Azman, membuka Youtube untuk mencari lagu Peterpan. [CMD:YOUTUBE:lagu peterpan]'.";

    public static void requestAiDecision(String provider, String apiKey, String userPrompt, final GroqResponseCallback callback) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                String targetUrl = "https://api.groq.com/openai/v1/chat/completions";
                String model = "llama-3.1-8b-instant";
                
                if ("gemini".equalsIgnoreCase(provider)) {
                    targetUrl = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
                } else if ("openrouter".equalsIgnoreCase(provider)) {
                    targetUrl = "https://openrouter.ai/api/v1/chat/completions";
                    model = "meta-llama/llama-3-8b-instruct:free";
                }

                String jsonPayload;
                if ("gemini".equalsIgnoreCase(provider)) {
                    JSONObject sysInst = new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", SYSTEM_PROMPT)));
                    JSONObject contentObj = new JSONObject().put("parts", new JSONArray().put(new JSONObject().put("text", userPrompt)));
                    jsonPayload = new JSONObject()
                            .put("systemInstruction", sysInst)
                            .put("contents", new JSONArray().put(contentObj))
                            .toString();
                } else {
                    JSONArray msgs = new JSONArray();
                    msgs.put(new JSONObject().put("role", "system").put("content", SYSTEM_PROMPT));
                    msgs.put(new JSONObject().put("role", "user").put("content", userPrompt));
                    jsonPayload = new JSONObject().put("model", model).put("messages", msgs).toString();
                }

                URL url = new URL(targetUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                if (!"gemini".equalsIgnoreCase(provider)) {
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                }
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() == 200) {
                    BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) response.append(line.trim());

                    String reply = "";
                    if ("gemini".equalsIgnoreCase(provider)) {
                        reply = new JSONObject(response.toString()).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text");
                    } else {
                        reply = new JSONObject(response.toString()).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                    }

                    // EKSTRAKSI KODE PERINTAH [CMD:...]
                    String extractedCmd = null;
                    String cleanSpeech = reply;
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[CMD:.*?\\]").matcher(reply);
                    if (m.find()) {
                        extractedCmd = m.group();
                        cleanSpeech = reply.replace(extractedCmd, "").trim(); // Hapus kode agar tidak dibacakan TTS
                    }

                    final String finalSpeech = cleanSpeech;
                    final String finalCmd = extractedCmd;
                    mainHandler.post(() -> callback.onSuccess(finalSpeech, finalCmd));
                } else {
                    mainHandler.post(() -> callback.onError(new Exception("Error API Kode: " + conn.getResponseCode())));
                }
            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(new Exception("Koneksi gagal.")));
            }
        }).start();
    }
}
