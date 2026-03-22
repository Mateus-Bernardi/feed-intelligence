package com.feedintelligence.observability;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "execution_ledger")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExecutionLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    private String status;

    @Column(name = "articles_found")
    private int articlesFound;

    @Column(name = "articles_skipped")
    private int articlesSkipped;

    @Column(columnDefinition = "TEXT")
    private String errors;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}