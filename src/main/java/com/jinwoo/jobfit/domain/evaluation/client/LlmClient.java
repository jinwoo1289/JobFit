package com.jinwoo.jobfit.domain.evaluation.client;

public interface LlmClient {
    String complete(String systemPrompt, String userPrompt);

    // 응답 형식을 JSON 스키마로 강제해야 하는 호출용. 구조화 출력을 지원하지 않는 구현(테스트 대역 등)은
    // 스키마 없이 일반 호출로 대체한다.
    default String complete(String systemPrompt, String userPrompt, JsonSchemaFormat responseSchema) {
        return complete(systemPrompt, userPrompt);
    }
}
