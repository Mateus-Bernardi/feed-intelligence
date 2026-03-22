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
public class OpenRouterSummarizerService implements AiSummarizerService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.openrouter.api-key}")
    private String apiKey;

    @Value("${app.openrouter.base-url}")
    private String baseUrl;

    @Value("${app.openrouter.model}")
    private String model;

    @Override
    public int getPriority() {
        return 1; // primário
    }

    @Override
    public String summarize(String title, String body) {
        try {
            String prompt = buildPrompt(title, body);

            // Monta o body da requisição no formato OpenAI-compatible
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 500);

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode message = messages.addObject();
            message.put("role", "user");
            message.put("content", prompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/chat/completions"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                throw new RateLimitException("OpenRouter rate limit reached");
            }

            if (response.statusCode() != 200) {
                throw new RuntimeException("OpenRouter error: " + response.statusCode());
            }

            JsonNode responseJson = objectMapper.readTree(response.body());
            return responseJson
                    .get("choices").get(0)
                    .get("message").get("content")
                    .asText();

        } catch (RateLimitException e) {
            throw e; // propaga para o worker tratar com backoff
        } catch (Exception e) {
            log.error("OpenRouter summarization failed: {}", e.getMessage());
            throw new RuntimeException("OpenRouter summarization failed", e);
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