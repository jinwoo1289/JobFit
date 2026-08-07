package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import jakarta.validation.constraints.NotNull;

public record EvaluationWeightRequest(
        @NotNull Integer skillWeight,
        @NotNull Integer experienceWeight,
        @NotNull Integer preferenceWeight,
        @NotNull Integer certificateWeight
) {
    public EvaluationWeight toEntity(UserProfile userProfile) {
        return new EvaluationWeight(userProfile, skillWeight, experienceWeight, preferenceWeight, certificateWeight);
    }
}
