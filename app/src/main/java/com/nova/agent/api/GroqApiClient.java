package com.nova.agent.api;

import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class GroqApiClient {
    private static final String BASE_URL = "https://api.groq.com/openai/";

    public interface GroqResponseCallback {
        void onSuccess(String aiResponseText);
        void onError(Throwable throwable);
    }

    public static void requestAiDecision(String apiKey, String prompt, final GroqResponseCallback callback) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        GroqApiService service = retrofit.create(GroqApiService.class);

        List<ChatRequest.Message> messages = new ArrayList<>();
        messages.add(new ChatRequest.Message("user", prompt));
        ChatRequest request = new ChatRequest("llama3-8b-8192", messages);

        String authHeader = "Bearer " + apiKey;

        service.getChatCompletion(authHeader, request).enqueue(new Callback<ChatResponse>() {
            @Override
            public void onResponse(Call<ChatResponse> call, Response<ChatResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ChatResponse.Choice> choices = response.body().getChoices();
                    if (choices != null && !choices.isEmpty()) {
                        String reply = choices.get(0).getMessage().getContent();
                        callback.onSuccess(reply);
                    } else {
                        callback.onError(new Exception("Response body choices list is empty"));
                    }
                } else {
                    callback.onError(new Exception("API Error: " + response.code() + " " + response.message()));
                }
            }

            @Override
            public void onFailure(Call<ChatResponse> call, Throwable t) {
                callback.onError(t);
            }
        });
    }
}
