package com.feedintelligence.collector;

import com.feedintelligence.article.Article;
import com.feedintelligence.article.ArticleRepository;
import com.feedintelligence.common.lock.DistributedLockService;
import com.feedintelligence.feed.FeedSource;
import com.feedintelligence.feed.FeedSourceRepository;
import com.feedintelligence.observability.ExecutionLedger;
import com.feedintelligence.observability.ExecutionLedgerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorJob {

    private static final String LOCK_KEY = "lock:collector";

    private final FeedSourceRepository feedSourceRepository;
    private final ArticleRepository articleRepository;
    private final DistributedLockService lockService;
    private final ExecutionLedgerService ledgerService;
    private final StringRedisTemplate redisTemplate;
    private final List<FetcherAdapter> fetchers; // Spring injeta todas as implementações

    @Scheduled(cron = "0 0 */2 * * *") // a cada 2 horas
    public void collect() {
        // Tenta adquirir o lock — evita execução paralela em múltiplas instâncias
        if (!lockService.acquireLock(LOCK_KEY, Duration.ofHours(2))) {
            log.warn("CollectorJob already running, skipping.");
            return;
        }

        ExecutionLedger ledger = ledgerService.start("COLLECTOR");
        int found = 0;
        int skipped = 0;

        try {
            List<FeedSource> sources = feedSourceRepository.findAllByActiveTrue();
            log.info("CollectorJob started — {} active sources", sources.size());

            for (FeedSource source : sources) {
                // Encontra o fetcher correto para o tipo da fonte
                FetcherAdapter fetcher = fetchers.stream()
                        .filter(f -> f.supports(source.getType()))
                        .findFirst()
                        .orElse(null);

                if (fetcher == null) {
                    log.warn("No fetcher found for type: {}", source.getType());
                    continue;
                }

                List<ArticleDTO> articles = fetcher.fetch(source);

                for (ArticleDTO dto : articles) {
                    String hash = computeHash(dto.title(), dto.body());

                    // Dedup — ignora se já existe para esse usuário
                    if (articleRepository.existsByUserIdAndContentHash(
                            dto.userId(), hash)) {
                        skipped++;
                        continue;
                    }

                    // Salva o artigo com status PENDING
                    Article article = Article.builder()
                            .user(source.getUser())
                            .source(source)
                            .url(dto.url())
                            .contentHash(hash)
                            .title(dto.title())
                            .body(dto.body())
                            .build();

                    articleRepository.save(article);

                    // Publica na fila do Redis para o SummarizerWorker processar
                    redisTemplate.opsForList()
                            .leftPush("queue:summarizer", article.getId().toString());

                    found++;
                }
            }

            ledgerService.finish(ledger, found, skipped);
            log.info("CollectorJob finished — found: {}, skipped: {}", found, skipped);

        } catch (Exception e) {
            ledgerService.fail(ledger, e.getMessage());
            log.error("CollectorJob failed: {}", e.getMessage(), e);
        } finally {
            // Sempre libera o lock, mesmo se der erro
            lockService.releaseLock(LOCK_KEY);
        }
    }

    // SHA-256 dos primeiros 200 chars do título + corpo
    private String computeHash(String title, String body) {
        try {
            String input = (title + (body != null ? body.substring(0,
                    Math.min(body.length(), 200)) : ""));
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Error computing content hash", e);
        }
    }
}

