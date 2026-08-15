package com.jinwoo.jobfit.domain.evaluation.client;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt);
}
