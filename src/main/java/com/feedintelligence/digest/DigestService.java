package com.feedintelligence.digest;

import com.feedintelligence.article.Article;
import com.feedintelligence.article.ArticleRepository;
import com.feedintelligence.article.ArticleStatus;
import com.feedintelligence.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DigestService {

    private final ArticleRepository articleRepository;
    private final EmailDispatcherService emailDispatcher;
    private final EmailRetryQueueRepository retryQueueRepository;

    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Transactional
    public void sendDigestForUser(User user) {
        // Busca artigos prontos que ainda não foram enviados
        List<Article> articles = articleRepository
                .findByUserIdAndStatusAndEmailSentFalse(
                        user.getId(), ArticleStatus.SUMMARIZED);

        if (articles.isEmpty()) {
            log.debug("No new articles for user: {}", user.getEmail());
            return;
        }

        String subject = "📰 Seu digest de hoje — "
                + LocalDateTime.now().format(DATE_FORMATTER);
        String html = buildHtml(user, articles);

        try {
            emailDispatcher.send(user.getEmail(), subject, html);

            // Marca todos os artigos como enviados
            articles.forEach(a -> a.setEmailSent(true));
            articleRepository.saveAll(articles);

            log.info("Digest sent to {} — {} articles", user.getEmail(), articles.size());

        } catch (Exception e) {
            log.error("Failed to send digest to {}: {}", user.getEmail(), e.getMessage());

            // Salva na fila de retry com o HTML já montado
            EmailRetryQueue retry = EmailRetryQueue.builder()
                    .user(user)
                    .payload(html)
                    .build();
            retryQueueRepository.save(retry);
        }
    }

    @Transactional
    public void processRetryQueue() {
        List<EmailRetryQueue> pending = retryQueueRepository
                .findByNextTryAtBeforeAndAttemptsLessThan(
                        LocalDateTime.now(), MAX_RETRY_ATTEMPTS);

        for (EmailRetryQueue entry : pending) {
            try {
                String subject = "📰 Seu digest — (reenvio)";
                emailDispatcher.send(entry.getUser().getEmail(), subject, entry.getPayload());

                // Sucesso — remove da fila
                retryQueueRepository.delete(entry);
                log.info("Retry email sent to {}", entry.getUser().getEmail());

            } catch (Exception e) {
                entry.setAttempts(entry.getAttempts() + 1);

                if (entry.getAttempts() >= MAX_RETRY_ATTEMPTS) {
                    // Falha permanente — remove da fila e loga
                    retryQueueRepository.delete(entry);
                    log.error("Digest permanently failed for user {} after {} attempts",
                            entry.getUser().getEmail(), MAX_RETRY_ATTEMPTS);
                } else {
                    // Agenda próxima tentativa com backoff exponencial
                    // tentativa 1 → +15min, tentativa 2 → +30min
                    long minutesDelay = 15L * (long) Math.pow(2, entry.getAttempts() - 1);
                    entry.setNextTryAt(LocalDateTime.now().plusMinutes(minutesDelay));
                    retryQueueRepository.save(entry);
                    log.warn("Retry failed for {}, next attempt in {}min",
                            entry.getUser().getEmail(), minutesDelay);
                }
            }
        }
    }

    // Monta o HTML do e-mail — simples e sem dependência de template engine
    private String buildHtml(User user, List<Article> articles) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Feed Intelligence Digest</title>
                    <style>
                        body { font-family: Arial, sans-serif; max-width: 600px;
                               margin: 0 auto; padding: 20px; color: #333; }
                        h1 { color: #1a1a2e; border-bottom: 2px solid #e94560;
                             padding-bottom: 10px; }
                        .article { margin-bottom: 30px; padding: 15px;
                                   background: #f9f9f9; border-radius: 8px;
                                   border-left: 4px solid #e94560; }
                        .article h2 { margin: 0 0 10px 0; font-size: 16px; color: #1a1a2e; }
                        .summary { white-space: pre-line; line-height: 1.6; }
                        .read-more { color: #e94560; text-decoration: none;
                                     font-size: 13px; }
                        .footer { margin-top: 40px; font-size: 12px; color: #999;
                                  text-align: center; }
                    </style>
                </head>
                <body>
                """);

        sb.append("<h1>📰 Seu digest de hoje</h1>");
        sb.append("<p>Olá! Aqui estão os resumos dos seus artigos de hoje, <strong>")
                .append(LocalDateTime.now().format(DATE_FORMATTER))
                .append("</strong>.</p>");

        for (Article article : articles) {
            sb.append("<div class='article'>");
            sb.append("<h2>").append(escapeHtml(article.getTitle())).append("</h2>");
            sb.append("<div class='summary'>")
                    .append(escapeHtml(article.getSummary()))
                    .append("</div>");

            if (article.getUrl() != null) {
                sb.append("<br><a class='read-more' href='")
                        .append(article.getUrl())
                        .append("'>Ler artigo completo →</a>");
            }
            sb.append("</div>");
        }

        sb.append("""
                    <div class='footer'>
                        <p>Você recebeu este e-mail porque tem uma conta no Feed Intelligence.</p>
                    </div>
                </body>
                </html>
                """);

        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}