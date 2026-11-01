package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

// notices는 지원 불가 사유가 아니라 판정과 별개로 사용자에게 보여줄 안내 문구다.
public record ConditionCheckResult(
        boolean passed,
        List<String> failedReasons,
        List<String> notices
) {
    public static ConditionCheckResult pass(List<String> notices) {
        return new ConditionCheckResult(true, List.of(), notices);
    }

    public static ConditionCheckResult fail(List<String> failedReasons, List<String> notices) {
        return new ConditionCheckResult(false, failedReasons, notices);
    }
}
