package com.feedintelligence.feed.dto;

import com.feedintelligence.feed.FeedSource;
import com.feedintelligence.feed.FeedType;

import java.time.LocalDateTime;
import java.util.UUID;

public record FeedSourceResponse(
        UUID id,
        FeedType type,
        String value,
        boolean active,
        LocalDateTime createdAt
) {
    // Converte entidade para DTO de resposta
    public static FeedSourceResponse from(FeedSource source) {
        return new FeedSourceResponse(
                source.getId(),
                source.getType(),
                source.getValue(),
                source.isActive(),
                source.getCreatedAt()
        );
    }
}