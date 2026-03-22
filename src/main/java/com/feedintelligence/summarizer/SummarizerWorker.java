package com.feedintelligence.summarizer;

import com.feedintelligence.article.Article;
import com.feedintelligence.article.ArticleRepository;
import com.feedintelligence.article.ArticleStatus;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SummarizerWorker {

    private static final String QUEUE_KEY = "queue:summarizer";
    private static final int MAX_ATTEMPTS = 3;

    private final StringRedisTemplate redisTemplate;
    private final ArticleRepository articleRepository;
    private final List<AiSummarizerService> summarizers;

    // Flag para controlar o loop quando a aplicação for desligada
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ExecutorService executor;

    @PostConstruct
    public void start() {
        running.set(true);

        // Thread dedicada — não bloqueia o pool do Spring
        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "summarizer-worker");
            t.setDaemon(true);
            return t;
        });

        executor.submit(this::workerLoop);
        log.info("SummarizerWorker started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("SummarizerWorker stopped");
    }

    private void workerLoop() {
        // Ordena os summarizers por prioridade (1 = primário, 2 = fallback)
        List<AiSummarizerService> ordered = summarizers.stream()
                .sorted(Comparator.comparingInt(AiSummarizerService::getPriority))
                .toList();

        while (running.get()) {
            try {
                // BRPOP bloqueia até 5 segundos esperando um item na fila
                // Se não vier nada, volta ao início do loop (não consome CPU)
                var result = redisTemplate.opsForList()
                        .rightPop(QUEUE_KEY, 5, TimeUnit.SECONDS);

                if (result == null) continue;

                processArticle(UUID.fromString(result), ordered);

            } catch (Exception e) {
                log.error("Unexpected error in summarizer worker loop: {}", e.getMessage());
                // Pequena pausa para não entrar em loop infinito em caso de erro persistente
                sleep(2000);
            }
        }
    }

    private void processArticle(UUID articleId, List<AiSummarizerService> ordered) {
        Article article = articleRepository.findById(articleId).orElse(null);

        if (article == null) {
            log.warn("Article not found in database: {}", articleId);
            return;
        }

        if (article.getStatus() != ArticleStatus.PENDING) {
            log.debug("Article {} already processed, skipping", articleId);
            return;
        }

        log.debug("Processing article: {}", article.getTitle());

        // Tenta cada summarizer em ordem de prioridade
        for (AiSummarizerService summarizer : ordered) {
            try {
                String summary = tryWithBackoff(summarizer, article);

                // Sucesso — salva o resumo
                article.setSummary(summary);
                article.setStatus(ArticleStatus.SUMMARIZED);
                article.setSummarizedAt(LocalDateTime.now());
                articleRepository.save(article);

                log.info("Article summarized successfully: {}", article.getTitle());
                return; // para de tentar outros summarizers

            } catch (RateLimitException e) {
                log.warn("Rate limit on {}, trying next summarizer",
                        summarizer.getClass().getSimpleName());
                // Tenta o próximo da lista (fallback)

            } catch (Exception e) {
                log.warn("Summarizer {} failed: {}",
                        summarizer.getClass().getSimpleName(), e.getMessage());
                // Tenta o próximo da lista (fallback)
            }
        }

        // Todos os summarizers falharam
        article.setAttempts(article.getAttempts() + 1);
        article.setLastAttemptAt(LocalDateTime.now());

        if (article.getAttempts() >= MAX_ATTEMPTS) {
            article.setStatus(ArticleStatus.FAILED_PERMANENT);
            log.error("Article {} failed permanently after {} attempts",
                    article.getTitle(), MAX_ATTEMPTS);
        } else {
            article.setStatus(ArticleStatus.FAILED);
            // Recoloca na fila para tentar de novo depois
            redisTemplate.opsForList()
                    .leftPush(QUEUE_KEY, articleId.toString());
            log.warn("Article {} failed (attempt {}), requeued",
                    article.getTitle(), article.getAttempts());
        }

        articleRepository.save(article);
    }

    // Tenta sumarizar com backoff exponencial em caso de rate limit
    private String tryWithBackoff(AiSummarizerService summarizer, Article article) {
        int maxRetries = 3;
        long delay = 1000; // começa com 1 segundo

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                return summarizer.summarize(article.getTitle(), article.getBody());
            } catch (RateLimitException e) {
                if (attempt == maxRetries) throw e;
                log.warn("Rate limit, waiting {}ms before retry {}/{}",
                        delay, attempt, maxRetries);
                sleep(delay);
                delay *= 2; // 1s → 2s → 4s
            }
        }

        throw new RateLimitException("Max retries exceeded");
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
