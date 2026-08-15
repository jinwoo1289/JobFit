package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 프롬프트에 포함된 공고 제목으로 사전에 등록된 요구 스킬(CSV)을 찾아
 * requiredSkills로 채운 JSON을 반환하는 테스트용 LlmClient.
 * jobCategory는 항상 BACKEND_DEVELOPMENT로 고정해 required-condition 판정에 영향을 주지 않는다.
 */
class FakeLlmClient implements LlmClient {

    private final Map<String, String> requiredSkillsCsvByTitle;

    FakeLlmClient(Map<String, String> requiredSkillsCsvByTitle) {
        this.requiredSkillsCsvByTitle = requiredSkillsCsvByTitle;
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        String csv = requiredSkillsCsvByTitle.entrySet().stream()
                .filter(entry -> userPrompt.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No fake response registered for prompt: " + userPrompt));

        String requiredSkillsJson = toJsonArray(csv);
        return """
                {"requiredSkills": %s, "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
                """.formatted(requiredSkillsJson);
    }

    private String toJsonArray(String csv) {
        if (csv == null || csv.isBlank()) {
            return "[]";
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .map(skill -> "\"" + skill + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
