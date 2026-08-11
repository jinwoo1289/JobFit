package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record EvaluationResult(
        Long jobPostingId,
        double totalScore,
        double skillScore,
        double experienceScore,
        EvaluationVerdict verdict,
        List<String> failedReasons
) {
    public static EvaluationResult unfit(Long jobPostingId, ScoreResult scoreResult, List<String> failedReasons) {
        return new EvaluationResult(
                jobPostingId, 0, scoreResult.skillScore(), scoreResult.experienceScore(),
                EvaluationVerdict.UNFIT, failedReasons
        );
    }

    public static EvaluationResult fit(Long jobPostingId, double totalScore, ScoreResult scoreResult) {
        return new EvaluationResult(
                jobPostingId, totalScore, scoreResult.skillScore(), scoreResult.experienceScore(),
                EvaluationVerdict.FIT, List.of()
        );
    }
}
