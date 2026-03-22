package com.feedintelligence.collector;

import com.feedintelligence.feed.FeedSource;

import java.util.List;

public interface FetcherAdapter {

    // Cada implementação busca artigos de um tipo de fonte
    List<ArticleDTO> fetch(FeedSource source);

    // Define qual tipo de fonte essa implementação suporta
    boolean supports(com.feedintelligence.feed.FeedType type);
}