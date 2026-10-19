package com.teximo.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Talks to OpenRouter. The API key is read from SecretStore and never leaves the backend. */
@Service
public class AiService {
    private static final String URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEFAULT_MODEL = "openai/gpt-4o-mini";

    private final SecretStore secrets;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    public AiService(SecretStore secrets) { this.secrets = secrets; }

    public boolean isConfigured() { return secrets.isSet(SecretStore.OPENROUTER_KEY); }

    public String chat(String system, String user) {
        return chat(List.of(Map.of("role", "system", "content", system), Map.of("role", "user", "content", user)));
    }

    public String chat(List<Map<String, String>> messages) {
        String key = secrets.get(SecretStore.OPENROUTER_KEY);
        if (key == null || key.isBlank())
            throw new IllegalArgumentException("The AI key is not configured yet. Ask your placement admin to add the OpenRouter key.");
        String model = secrets.get(SecretStore.OPENROUTER_MODEL);
        if (model == null || model.isBlank()) model = DEFAULT_MODEL;
        try {
            String body = mapper.writeValueAsString(Map.of("model", model, "messages", messages, "max_tokens", 900));
            HttpRequest req = HttpRequest.newBuilder(URI.create(URL))
                    .timeout(Duration.ofSeconds(60))
                    .header("Authorization", "Bearer " + key)
                    .header("Content-Type", "application/json")
                    .header("X-Title", "Teximo")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 401) throw new IllegalArgumentException("OpenRouter rejected the API key.");
            if (res.statusCode() >= 400) throw new IllegalArgumentException("OpenRouter returned an error (" + res.statusCode() + ").");
            JsonNode root = mapper.readTree(res.body());
            return root.path("choices").path(0).path("message").path("content").asString("");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not reach the AI service. Please try again.");
        }
    }

    /** Used by the admin "Test connection" button. */
    public String test() {
        chat("Reply with the single word OK.", "ping");
        return "OpenRouter connection works.";
    }
}
