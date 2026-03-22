package com.feedintelligence.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateUserRequest(
        @Min(value = 0, message = "Digest hour must be between 0 and 23")
        @Max(value = 23, message = "Digest hour must be between 0 and 23")
        Integer digestHour
) {
}