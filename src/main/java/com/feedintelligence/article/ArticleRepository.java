package com.feedintelligence.article;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {

    boolean existsByUserIdAndContentHash(UUID userId, String contentHash);

    List<Article> findByUserIdAndStatusAndEmailSentFalse(
            UUID userId, ArticleStatus status);

    Page<Article> findByUserIdOrderByCollectedAtDesc(UUID userId, Pageable pageable);

    long countByStatus(ArticleStatus status);
}