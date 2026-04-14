package com.tradingservice.aiservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Client for Anthropic API (Claude models).
 * Used for lesson generation and strategy generation.
 */
@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public AnthropicClient(
            @Value("${anthropic.api.key:}") String apiKey,
            @Value("${anthropic.api.base-url:https://api.anthropic.com/v1}") String baseUrl,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }

    @PostConstruct
    public void validateConfig() {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("ANTHROPIC_API_KEY is not configured — AI generation features will be disabled");
        }
    }

    /**
     * Generate a lesson from a trading result (Haiku model, max 40 tokens).
     */
    public String generateLesson(String prompt) throws IOException {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Anthropic API key not configured");
            return null;
        }

        String requestBody = objectMapper.writeValueAsString(new LessonRequest(
                "claude-haiku-4-5-20251001",
                40,
                new Message[]{new Message("user", prompt)}
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("Anthropic API returned {}: {}", response.code(), response.body() != null ? response.body().string() : "empty");
                return null;
            }

            String responseBody = response.body().string();
            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode content = json.path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText();
            }
            return null;
        }
    }

    /**
     * Generate a complete strategy from memories and market context (Sonnet model).
     */
    public String generateStrategy(String systemPrompt, String userPrompt) throws IOException {
        if (apiKey == null || apiKey.isEmpty()) {
            log.warn("Anthropic API key not configured");
            return null;
        }

        String requestBody = objectMapper.writeValueAsString(new StrategyRequest(
                "claude-sonnet-4-6",
                1024,
                systemPrompt,
                new Message[]{new Message("user", userPrompt)}
        ));

        Request request = new Request.Builder()
                .url(baseUrl + "/messages")
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "empty";
            if (!response.isSuccessful()) {
                log.error("Anthropic API returned {}: {}", response.code(), responseBody);
                throw new IOException("Anthropic API error: " + response.code());
            }

            JsonNode json = objectMapper.readTree(responseBody);
            JsonNode content = json.path("content");
            if (content.isArray() && content.size() > 0) {
                return content.get(0).path("text").asText();
            }
            throw new IOException("Invalid response format from Anthropic API");
        }
    }

    // Request DTOs
    private record LessonRequest(String model, int max_tokens, Message[] messages) {}

    private record StrategyRequest(String model, int max_tokens, String system, Message[] messages) {}

    private record Message(String role, String content) {}
}
