package com.feedintelligence.feed;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedSourceRepository extends JpaRepository<FeedSource, UUID> {

    List<FeedSource> findByUserIdAndActiveTrue(UUID userId);

    List<FeedSource> findAllByActiveTrue();

    Optional<FeedSource> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndValue(UUID userId, String value);
}