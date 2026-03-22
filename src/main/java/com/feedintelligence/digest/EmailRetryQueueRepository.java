package com.feedintelligence.digest;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmailRetryQueueRepository extends JpaRepository<EmailRetryQueue, UUID> {

    // Busca entradas prontas para retentar
    List<EmailRetryQueue> findByNextTryAtBeforeAndAttemptsLessThan(
            LocalDateTime now, int maxAttempts);
}