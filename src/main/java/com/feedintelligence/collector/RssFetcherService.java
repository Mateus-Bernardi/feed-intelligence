package com.feedintelligence.collector;

import com.feedintelligence.feed.FeedSource;
import com.feedintelligence.feed.FeedType;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
public class RssFetcherService implements FetcherAdapter {

    // HttpClient nativo do Java 11+ — simples e eficiente
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public boolean supports(FeedType type) {
        return type == FeedType.RSS;
    }

    @Override
    public List<ArticleDTO> fetch(FeedSource source) {
        try {
            log.debug("Fetching RSS feed: {}", source.getValue());

            // Faz a requisição HTTP com timeout de 5 segundos
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.getValue()))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                log.warn("RSS feed returned status {}: {}",
                        response.statusCode(), source.getValue());
                return Collections.emptyList();
            }

            // Rome parseia o feed RSS/Atom
            SyndFeedInput input = new SyndFeedInput();
            var feed = input.build(new XmlReader(response.body()));

            // Converte cada entrada do feed em ArticleDTO
            return feed.getEntries().stream()
                    .map(entry -> toArticleDTO(entry, source))
                    .toList();

        } catch (Exception e) {
            log.error("Error fetching RSS feed {}: {}", source.getValue(), e.getMessage());
            return Collections.emptyList();
        }
    }

    private ArticleDTO toArticleDTO(SyndEntry entry, FeedSource source) {
        // Extrai o corpo do artigo — tenta description, depois contents
        String body = "";
        if (entry.getDescription() != null) {
            body = entry.getDescription().getValue();
        } else if (!entry.getContents().isEmpty()) {
            body = entry.getContents().get(0).getValue();
        }

        // Remove tags HTML do corpo
        body = body.replaceAll("<[^>]*>", "").trim();

        return new ArticleDTO(
                source.getUser().getId(),
                source.getId(),
                entry.getLink(),
                entry.getTitle(),
                body
        );
    }
}