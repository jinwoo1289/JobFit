package com.jinwoo.jobfit.domain.evaluation.dto;

import jakarta.validation.constraints.NotNull;

public record EvaluationRequest(
        @NotNull
        Long userProfileId
) {
}
