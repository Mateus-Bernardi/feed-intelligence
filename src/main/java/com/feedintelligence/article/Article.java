package com.feedintelligence.article;

import com.feedintelligence.feed.FeedSource;
import com.feedintelligence.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "articles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_id")
    private FeedSource source;

    @Column(nullable = false)
    private String url;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ArticleStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "last_attempt_at")
    private LocalDateTime lastAttemptAt;

    @Column(name = "email_sent", nullable = false)
    private boolean emailSent;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "summarized_at")
    private LocalDateTime summarizedAt;

    @PrePersist
    protected void onCreate() {
        this.collectedAt = LocalDateTime.now();
        this.status = ArticleStatus.PENDING;
        this.attempts = 0;
        this.emailSent = false;
    }
}