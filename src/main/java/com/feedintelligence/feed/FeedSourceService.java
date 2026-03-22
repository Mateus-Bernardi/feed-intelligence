package com.feedintelligence.feed;

import com.feedintelligence.common.exception.AppException;
import com.feedintelligence.feed.dto.*;
import com.feedintelligence.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedSourceService {

    private final FeedSourceRepository feedSourceRepository;
    private final UserRepository userRepository;

    public List<FeedSourceResponse> listByUser(UUID userId) {
        return feedSourceRepository.findByUserIdAndActiveTrue(userId)
                .stream()
                .map(FeedSourceResponse::from)
                .toList();
    }

    @Transactional
    public FeedSourceResponse create(UUID userId, FeedSourceRequest request) {
        // Verifica duplicata
        if (feedSourceRepository.existsByUserIdAndValue(userId, request.value())) {
            throw AppException.conflict("Feed source already exists: " + request.value());
        }

        var user = userRepository.findById(userId)
                .orElseThrow(() -> AppException.notFound("User not found"));

        var source = FeedSource.builder()
                .user(user)
                .type(request.type())
                .value(request.value())
                .build();

        var saved = feedSourceRepository.save(source);
        log.info("Feed source created for user {}: {}", userId, request.value());

        return FeedSourceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID sourceId) {
        var source = feedSourceRepository.findByIdAndUserId(sourceId, userId)
                .orElseThrow(() -> AppException.notFound("Feed source not found"));

        source.setActive(false);
        feedSourceRepository.save(source);

        log.info("Feed source deleted for user {}: {}", userId, sourceId);
    }
}