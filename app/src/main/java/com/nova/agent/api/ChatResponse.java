package com.nova.agent.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Representasi model JSON kembalian dari endpoint API Groq Chat Completion.
 */
public class ChatResponse {

    @SerializedName("id")
    private String id;

    @SerializedName("choices")
    private List<Choice> choices;

    public String getId() {
        return id;
    }

    public List<Choice> getChoices() {
        return choices;
    }

    /**
     * Representasi pilihan respon yang dikembalikan oleh model AI
     */
    public static class Choice {
        @SerializedName("index")
        private int index;

        @SerializedName("message")
        private ChatRequest.Message message;

        @SerializedName("finish_reason")
        private String finishReason;

        public int getIndex() {
            return index;
        }

        public ChatRequest.Message getMessage() {
            return message;
        }

        public String getFinishReason() {
            return finishReason;
        }
    }
}