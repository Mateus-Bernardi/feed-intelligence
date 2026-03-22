package com.feedintelligence.summarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@Service
public class GeminiSummarizerService implements AiSummarizerService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.gemini.api-key}")
    private String apiKey;

    @Value("${app.gemini.base-url}")
    private String baseUrl;

    @Value("${app.gemini.model}")
    private String model;

    @Override
    public int getPriority() {
        return 2; // fallback
    }

    @Override
    public String summarize(String title, String body) {
        try {
            String prompt = buildPrompt(title, body);

            // Gemini tem formato de request diferente do OpenAI
            ObjectNode requestBody = objectMapper.createObjectNode();
            ArrayNode contents = requestBody.putArray("contents");
            ObjectNode content = contents.addObject();
            ArrayNode parts = content.putArray("parts");
            ObjectNode part = parts.addObject();
            part.put("text", prompt);

            // URL da Gemini inclui o model e a action
            String url = String.format("%s/%s:generateContent?key=%s",
                    baseUrl, model, apiKey);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Gemini error: " + response.statusCode()
                        + " — " + response.body());
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            return responseJson
                    .get("candidates").get(0)
                    .get("content")
                    .get("parts").get(0)
                    .get("text")
                    .asText();

        } catch (Exception e) {
            log.error("Gemini summarization failed: {}", e.getMessage());
            throw new RuntimeException("Gemini summarization failed", e);
        }
    }

    private String buildPrompt(String title, String body) {
        String content = body != null && !body.isBlank()
                ? body.substring(0, Math.min(body.length(), 1500))
                : "Sem conteúdo disponível.";

        return """
                Resuma o seguinte artigo em 3 bullet points em português,
                destacando os pontos mais importantes.
                Responda APENAS com os bullet points, sem introdução.
                
                Título: %s
                
                Conteúdo: %s
                """.formatted(title, content);
    }
}