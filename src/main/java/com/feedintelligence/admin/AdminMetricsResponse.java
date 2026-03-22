package com.feedintelligence.admin;

public record AdminMetricsResponse(
        long totalUsers,
        long totalArticles,
        long summarizedArticles,
        long pendingArticles,
        long failedArticles,
        long totalFeedSources
) {
}