package com.feedintelligence.feed;

import com.feedintelligence.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "feed_sources")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private FeedType type;

    @Column(nullable = false)
    private String value;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.active = true;
    }
}