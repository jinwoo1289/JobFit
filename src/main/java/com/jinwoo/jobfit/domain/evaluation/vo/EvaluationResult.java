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
        RequiredSkillMatch skillMatch
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
                EvaluationVerdict.UNFIT, failedReasons, notices, reasoning, expired, skillMatch
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
                EvaluationVerdict.INSUFFICIENT_INFO, List.of(), notices, reasoning, false, skillMatch
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
                EvaluationVerdict.FIT, List.of(), notices, reasoning, false, skillMatch
        );
    }
}
