package com.feedintelligence.admin;

import com.feedintelligence.article.ArticleRepository;
import com.feedintelligence.article.ArticleStatus;
import com.feedintelligence.feed.FeedSourceRepository;
import com.feedintelligence.observability.ExecutionLedger;
import com.feedintelligence.observability.ExecutionLedgerRepository;
import com.feedintelligence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final ExecutionLedgerRepository ledgerRepository;
    private final UserRepository userRepository;
    private final ArticleRepository articleRepository;
    private final FeedSourceRepository feedSourceRepository;

    @GetMapping("/ledger")
    public Page<ExecutionLedger> getLedger(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ledgerRepository.findAll(
                PageRequest.of(page, size,
                        Sort.by("startedAt").descending()));
    }

    @GetMapping("/metrics")
    public AdminMetricsResponse getMetrics() {
        return new AdminMetricsResponse(
                userRepository.count(),
                articleRepository.count(),
                articleRepository.countByStatus(ArticleStatus.SUMMARIZED),
                articleRepository.countByStatus(ArticleStatus.PENDING),
                articleRepository.countByStatus(ArticleStatus.FAILED),
                feedSourceRepository.count()
        );
    }
}