package com.feedintelligence.feed.dto;

import com.feedintelligence.feed.FeedType;
import jakarta.validation.constraints.*;

public record FeedSourceRequest(

        @NotNull(message = "Type is required")
        FeedType type,

        @NotBlank(message = "Value is required")
        @Size(max = 512, message = "Value must be at most 512 characters")
        String value
) {
}