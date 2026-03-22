package com.feedintelligence.collector;

import java.util.UUID;

public record ArticleDTO(
        UUID userId,
        UUID sourceId,
        String url,
        String title,
        String body
) {
}