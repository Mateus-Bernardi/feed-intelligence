package com.feedintelligence.collector;

import com.feedintelligence.feed.FeedSource;
import com.feedintelligence.feed.FeedType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class NewsApiFetcherService implements FetcherAdapter {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.news-api.api-key}")
    private String apiKey;

    @Override
    public boolean supports(FeedType type) {
        return type == FeedType.KEYWORD;
    }

    @Override
    public List<ArticleDTO> fetch(FeedSource source) {
        try {
            String keyword = source.getValue();
            log.debug("Fetching NewsAPI for keyword: {}", keyword);

            // Monta a URL da NewsAPI
            String url = String.format(
                    "https://newsapi.org/v2/everything?q=%s&language=pt&sortBy=publishedAt&pageSize=10&apiKey=%s",
                    keyword.replace(" ", "+"), apiKey);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("NewsAPI returned status {}: {}",
                        response.statusCode(), keyword);
                return Collections.emptyList();
            }

            // Parseia o JSON de resposta
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode articles = root.get("articles");

            if (articles == null || !articles.isArray()) {
                return Collections.emptyList();
            }

            List<ArticleDTO> result = new ArrayList<>();
            for (JsonNode article : articles) {
                String articleUrl = getText(article, "url");
                String title = getText(article, "title");
                String body = getText(article, "content");

                if (articleUrl == null || title == null) continue;

                result.add(new ArticleDTO(
                        source.getUser().getId(),
                        source.getId(),
                        articleUrl,
                        title,
                        body != null ? body : ""
                ));
            }

            return result;

        } catch (Exception e) {
            log.error("Error fetching NewsAPI for {}: {}",
                    source.getValue(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value != null && !value.isNull()) ? value.asText() : null;
    }
}