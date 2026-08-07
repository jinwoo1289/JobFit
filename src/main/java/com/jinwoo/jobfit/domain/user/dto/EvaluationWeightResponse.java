package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;

public record EvaluationWeightResponse(
        Long id,
        int skillWeight,
        int experienceWeight,
        int preferenceWeight,
        int certificateWeight
) {
    public static EvaluationWeightResponse from(EvaluationWeight entity) {
        return new EvaluationWeightResponse(
                entity.getId(),
                entity.getSkillWeight(),
                entity.getExperienceWeight(),
                entity.getPreferenceWeight(),
                entity.getCertificateWeight()
        );
    }
}
