package com.jinwoo.jobfit.domain.user.dto;

import jakarta.validation.constraints.NotNull;

public record EvaluationWeightUpdateRequest(
        @NotNull Integer skillWeight,
        @NotNull Integer experienceWeight,
        @NotNull Integer preferenceWeight,
        @NotNull Integer certificateWeight
) {
}
