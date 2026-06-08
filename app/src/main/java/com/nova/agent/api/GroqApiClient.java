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
        void onSuccess(final String aiResponseText, final String extractedCommand);
        void onError(Throwable throwable);
    }

    // PROMPT CANGGIH: Mengajari AI cara memecah tugas menjadi makro
    private static final String SYSTEM_PROMPT = 
        "Kamu adalah NOVA AI, asisten virtual Android cerdas buatan 'Moch Khoirul Azman'. " +
        "Berikan jawaban suara yang singkat dan natural dalam Bahasa Indonesia. " +
        "PENTING: Jika pengguna meminta interaksi HP yang rumit (seperti membalas chat), " +
        "kamu WAJIB merangkai KODE MAKRO di akhir teksmu (kode ini akan dieksekusi diam-diam). " +
        "Kombinasi Kode yang Tersedia: " +
        "[CMD:OPEN:nama_app] : Buka aplikasi " +
        "[CMD:TYPE:teks] : Mengetik teks ke dalam kotak input " +
        "[CMD:CLICK:nama_tombol] : Mengklik tombol " +
        "[CMD:YOUTUBE:kueri] : Buka dan cari di Youtube " +
        "[CMD:SHOPEE:kueri] : Buka dan cari di Shopee " +
        "[CMD:SCROLL_DOWN] : Mengusap layar ke bawah " +
        "CONTOH JIKA USER MEMINTA BALAS WA 'Halo': 'Baik Bos, mengirim pesan sekarang. [CMD:OPEN:whatsapp] [CMD:TYPE:Halo] [CMD:CLICK:Kirim]'.";

    public static void requestAiDecision(final String provider, final String apiKey, final String userPrompt, final GroqResponseCallback callback) {
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        
        new Thread(new Runnable() {
            @Override
            public void run() {
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
                        jsonPayload = new JSONObject().put("systemInstruction", sysInst).put("contents", new JSONArray().put(contentObj)).toString();
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

                        // EKSTRAKSI SEMUA KODE MAKRO SEKALIGUS
                        String extractedCmds = "";
                        String cleanSpeech = reply;
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[CMD:[^\\]]+\\]").matcher(reply);
                        while (m.find()) {
                            String cmd = m.group();
                            extractedCmds += cmd + " ";
                            cleanSpeech = cleanSpeech.replace(cmd, "").trim();
                        }

                        final String finalSpeech = cleanSpeech;
                        final String finalCmds = extractedCmds.isEmpty() ? null : extractedCmds.trim();
                        
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() { callback.onSuccess(finalSpeech, finalCmds); }
                        });
                    } else {
                        final int errCode = conn.getResponseCode();
                        mainHandler.post(new Runnable() {
                            @Override
                            public void run() { callback.onError(new Exception("Error API: " + errCode)); }
                        });
                    }
                } catch (final Exception e) {
                    mainHandler.post(new Runnable() {
                        @Override
                        public void run() { callback.onError(e); }
                    });
                }
            }
        }).start();
    }
}
