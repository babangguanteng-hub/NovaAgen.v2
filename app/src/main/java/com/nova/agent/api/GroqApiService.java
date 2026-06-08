package com.nova.agent.api;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface GroqApiService {
    @POST("v1/chat/completions")
    Call<ChatResponse> getChatCompletion(
        @Header("Authorization") String authorization,
        @Body ChatRequest request
    );
}
