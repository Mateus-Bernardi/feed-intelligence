package com.feedintelligence.digest;

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
public class EmailDispatcherService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${RESEND_API_KEY:placeholder}")
    private String apiKey;

    @Value("${app.email.from}")
    private String fromEmail;

    public void send(String toEmail, String subject, String htmlContent) {
        try {
            // Monta o body da requisição para a API do Resend
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("from", "Feed Intelligence <" + fromEmail + ">");
            requestBody.put("subject", subject);
            requestBody.put("html", htmlContent);

            ArrayNode to = requestBody.putArray("to");
            to.add(toEmail);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(requestBody)))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new RuntimeException("Resend API error: "
                        + response.statusCode() + " — " + response.body());
            }

            // Extrai o ID do e-mail enviado para log
            JsonNode responseJson = objectMapper.readTree(response.body());
            String emailId = responseJson.has("id")
                    ? responseJson.get("id").asText() : "unknown";

            log.info("Email sent to {} — Resend ID: {}", toEmail, emailId);

        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Email sending failed", e);
        }
    }
}