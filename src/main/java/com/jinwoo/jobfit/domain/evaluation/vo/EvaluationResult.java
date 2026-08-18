package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record EvaluationResult(
        Long jobPostingId,
        double totalScore,
        double skillScore,
        double experienceScore,
        double preferenceScore,
        double certificateScore,
        EvaluationVerdict verdict,
        List<String> failedReasons,
        EvaluationReasoning reasoning
) {
    public static EvaluationResult unfit(Long jobPostingId, ScoreResult scoreResult, List<String> failedReasons,
                                          EvaluationReasoning reasoning) {
        return new EvaluationResult(
                jobPostingId, 0, scoreResult.skillScore(), scoreResult.experienceScore(),
                scoreResult.preferenceScore(), scoreResult.certificateScore(),
                EvaluationVerdict.UNFIT, failedReasons, reasoning
        );
    }

    public static EvaluationResult fit(Long jobPostingId, double totalScore, ScoreResult scoreResult,
                                        EvaluationReasoning reasoning) {
        return new EvaluationResult(
                jobPostingId, totalScore, scoreResult.skillScore(), scoreResult.experienceScore(),
                scoreResult.preferenceScore(), scoreResult.certificateScore(),
                EvaluationVerdict.FIT, List.of(), reasoning
        );
    }
}
