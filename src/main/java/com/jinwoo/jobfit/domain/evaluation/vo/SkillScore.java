package com.jinwoo.jobfit.domain.evaluation.vo;

public record SkillScore(double value, boolean excluded) {

    private static final double EXCLUDED_VALUE = 0;

    public static SkillScore notEvaluated() {
        return new SkillScore(EXCLUDED_VALUE, true);
    }

    public static SkillScore of(double value) {
        return new SkillScore(value, false);
    }
}
