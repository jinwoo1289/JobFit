package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record EvaluationResult(
        Long jobPostingId,
        double totalScore,
        double skillScore, boolean skillExcluded,
        double experienceScore, boolean experienceExcluded,
        double preferenceScore, boolean preferenceExcluded,
        double certificateScore, boolean certificateExcluded,
        EvaluationVerdict verdict,
        List<String> failedReasons,
        List<String> notices,
        EvaluationReasoning reasoning,
        boolean expired,
        RequiredSkillMatch skillMatch,
        int preferredSkillCount,
        int preferredMatchedCount,
        // 필수 요건이 적어 우대 비중을 높여 총점을 계산했는지 여부. 총점이 계산되는 FIT일 때만 true일 수 있다.
        boolean thinRequired
) {
    public static EvaluationResult unfit(Long jobPostingId, ScoreResult scoreResult, List<String> failedReasons,
                                          List<String> notices, EvaluationReasoning reasoning,
                                          boolean expired, RequiredSkillMatch skillMatch) {
        return new EvaluationResult(
                jobPostingId, 0,
                scoreResult.skillScore(), scoreResult.skillExcluded(),
                scoreResult.experienceScore(), scoreResult.experienceExcluded(),
                scoreResult.preferenceScore(), scoreResult.preferenceExcluded(),
                scoreResult.certificateScore(), scoreResult.certificateExcluded(),
                EvaluationVerdict.UNFIT, failedReasons, notices, reasoning, expired, skillMatch,
                scoreResult.preferredSkillCount(), scoreResult.preferredMatchedCount(), false
        );
    }

    public static EvaluationResult insufficientInfo(Long jobPostingId, ScoreResult scoreResult,
                                                      List<String> notices, EvaluationReasoning reasoning,
                                                      RequiredSkillMatch skillMatch) {
        return new EvaluationResult(
                jobPostingId, 0,
                scoreResult.skillScore(), scoreResult.skillExcluded(),
                scoreResult.experienceScore(), scoreResult.experienceExcluded(),
                scoreResult.preferenceScore(), scoreResult.preferenceExcluded(),
                scoreResult.certificateScore(), scoreResult.certificateExcluded(),
                EvaluationVerdict.INSUFFICIENT_INFO, List.of(), notices, reasoning, false, skillMatch,
                scoreResult.preferredSkillCount(), scoreResult.preferredMatchedCount(), false
        );
    }

    public static EvaluationResult fit(Long jobPostingId, double totalScore, ScoreResult scoreResult,
                                        List<String> notices, EvaluationReasoning reasoning,
                                        RequiredSkillMatch skillMatch) {
        return new EvaluationResult(
                jobPostingId, totalScore,
                scoreResult.skillScore(), scoreResult.skillExcluded(),
                scoreResult.experienceScore(), scoreResult.experienceExcluded(),
                scoreResult.preferenceScore(), scoreResult.preferenceExcluded(),
                scoreResult.certificateScore(), scoreResult.certificateExcluded(),
                EvaluationVerdict.FIT, List.of(), notices, reasoning, false, skillMatch,
                scoreResult.preferredSkillCount(), scoreResult.preferredMatchedCount(), scoreResult.thinRequired()
        );
    }
}
