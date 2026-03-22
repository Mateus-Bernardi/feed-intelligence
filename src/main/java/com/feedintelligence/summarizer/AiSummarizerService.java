package com.feedintelligence.summarizer;

public interface AiSummarizerService {

    String summarize(String title, String body);

    int getPriority();
}