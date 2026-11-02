package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

// notices는 지원 불가 사유가 아니라 판정과 별개로 사용자에게 보여줄 안내 문구다.
// expired는 화면이 사유 문자열에 기대지 않고 마감 공고를 분리할 수 있도록 따로 담는다(마감이면 항상 passed=false).
public record ConditionCheckResult(
        boolean passed,
        List<String> failedReasons,
        List<String> notices,
        boolean expired
) {
    public static ConditionCheckResult pass(List<String> notices) {
        return new ConditionCheckResult(true, List.of(), notices, false);
    }

    public static ConditionCheckResult fail(List<String> failedReasons, List<String> notices, boolean expired) {
        return new ConditionCheckResult(false, failedReasons, notices, expired);
    }
}
