package com.jinwoo.jobfit.domain.evaluation.vo;

public record ScoreResult(
        double skillScore, boolean skillExcluded,
        double experienceScore, boolean experienceExcluded,
        double preferenceScore, boolean preferenceExcluded,
        double certificateScore, boolean certificateExcluded
) {
    public boolean allExcluded() {
        return skillExcluded && experienceExcluded && preferenceExcluded && certificateExcluded;
    }
}
