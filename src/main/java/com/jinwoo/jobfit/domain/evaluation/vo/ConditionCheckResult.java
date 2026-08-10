package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record ConditionCheckResult(
        boolean passed,
        List<String> failedReasons
) {
    public static ConditionCheckResult pass() {
        return new ConditionCheckResult(true, List.of());
    }

    public static ConditionCheckResult fail(List<String> failedReasons) {
        return new ConditionCheckResult(false, failedReasons);
    }
}
