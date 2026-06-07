package com.nova.agent.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/**
 * Definisi kontrak endpoint HTTP POST untuk layanan kecerdasan buatan Groq.
 */
public interface GroqApiService {

    /**
     * Mengirimkan payload percakapan ke Groq Cloud API
     * * @param authorizationHeader Header dengan nilai "Bearer [API_KEY]"
     * @param request Payload ChatRequest yang berisi model dan daftar pesan
     * @return Retrofit Call tobjek untuk pemrosesan sinkron/asinkron
     */
    @POST("openai/v1/chat/completions")
    Call<ChatResponse> getChatCompletion(
            @Header("Authorization") String authorizationHeader,
            @Body ChatRequest request
    );
}