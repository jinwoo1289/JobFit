package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record EvaluationReasoning(
        List<String> matchedPoints,
        List<String> gapPoints,
        List<String> checkPoints
) {
    public static EvaluationReasoning empty() {
        return new EvaluationReasoning(List.of(), List.of(), List.of());
    }
}
