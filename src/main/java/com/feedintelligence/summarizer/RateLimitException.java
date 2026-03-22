package com.feedintelligence.summarizer;

public class RateLimitException extends RuntimeException {
    public RateLimitException(String message) {
        super(message);
    }
}