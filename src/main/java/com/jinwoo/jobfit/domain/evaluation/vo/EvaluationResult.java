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
        List<String> failedReasons
) {
    public static EvaluationResult unfit(Long jobPostingId, ScoreResult scoreResult, List<String> failedReasons) {
        return new EvaluationResult(
                jobPostingId, 0, scoreResult.skillScore(), scoreResult.experienceScore(),
                scoreResult.preferenceScore(), scoreResult.certificateScore(),
                EvaluationVerdict.UNFIT, failedReasons
        );
    }

    public static EvaluationResult fit(Long jobPostingId, double totalScore, ScoreResult scoreResult) {
        return new EvaluationResult(
                jobPostingId, totalScore, scoreResult.skillScore(), scoreResult.experienceScore(),
                scoreResult.preferenceScore(), scoreResult.certificateScore(),
                EvaluationVerdict.FIT, List.of()
        );
    }
}
