package com.feedintelligence.article;

import com.feedintelligence.common.exception.AppException;
import com.feedintelligence.summarizer.SummarizerWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;

    public Page<ArticleResponse> listByUser(UUID userId, Pageable pageable) {
        return articleRepository
                .findByUserIdOrderByCollectedAtDesc(userId, pageable)
                .map(ArticleResponse::from);
    }

    public ArticleResponse getByIdAndUser(UUID id, UUID userId) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> AppException.notFound("Article not found"));

        // Garante que o usuário só acessa os próprios artigos
        if (!article.getUser().getId().equals(userId)) {
            throw AppException.forbidden("Access denied");
        }

        return ArticleResponse.from(article);
    }
}