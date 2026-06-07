package com.nova.agent.api;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/**
 * Representasi model data request untuk dikirimkan ke REST API Groq Chat Completion.
 */
public class ChatRequest {

    @SerializedName("model")
    private final String model;

    @SerializedName("messages")
    private final List<Message> messages;

    @SerializedName("temperature")
    private final double temperature;

    public ChatRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
        this.temperature = 0.5; // Menjaga respons tetap terstruktur dan konsisten
    }

    public String getModel() {
        return model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public double getTemperature() {
        return temperature;
    }

    /**
     * Sub-kelas representasi skema pesan chat (role & content)
     */
    public static class Message {
        @SerializedName("role")
        private final String role;

        @SerializedName("content")
        private final String content;

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public String getContent() {
            return content;
        }
    }
}