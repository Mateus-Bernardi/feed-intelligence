package com.feedintelligence.article;

import java.time.LocalDateTime;
import java.util.UUID;

public record ArticleResponse(
        UUID id,
        String title,
        String url,
        String summary,
        ArticleStatus status,
        LocalDateTime collectedAt,
        LocalDateTime summarizedAt
) {
    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getUrl(),
                article.getSummary(),
                article.getStatus(),
                article.getCollectedAt(),
                article.getSummarizedAt()
        );
    }
}