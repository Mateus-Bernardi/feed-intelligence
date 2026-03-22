package com.feedintelligence.digest;

import com.feedintelligence.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_retry_queue")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailRetryQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // HTML do e-mail serializado

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_try_at", nullable = false)
    private LocalDateTime nextTryAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.attempts = 0;
        this.nextTryAt = LocalDateTime.now();
    }
}