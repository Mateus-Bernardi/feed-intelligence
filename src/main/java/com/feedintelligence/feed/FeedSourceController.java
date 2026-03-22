package com.feedintelligence.feed;

import com.feedintelligence.feed.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feeds")
@RequiredArgsConstructor
public class FeedSourceController {

    private final FeedSourceService feedSourceService;

    @GetMapping
    public List<FeedSourceResponse> list(@AuthenticationPrincipal UUID userId) {
        return feedSourceService.listByUser(userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FeedSourceResponse create(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody FeedSourceRequest request) {
        return feedSourceService.create(userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID id) {
        feedSourceService.delete(userId, id);
    }
}