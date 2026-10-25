package com.jinwoo.jobfit.domain.evaluation.client;

import java.util.Map;

/**
 * OpenAI Structured Outputs(response_format: json_schema, strict)에 넘길 응답 스키마.
 * strict 모드는 모든 프로퍼티를 required로, additionalProperties를 false로 선언해야 한다.
 */
public record JsonSchemaFormat(String name, Map<String, Object> schema) {
}
