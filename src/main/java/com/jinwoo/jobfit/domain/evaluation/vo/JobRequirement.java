package com.jinwoo.jobfit.domain.evaluation.vo;

import com.jinwoo.jobfit.domain.user.entity.EmploymentType;

public record JobRequirement(
        Integer minYears,
        Integer maxYears,
        boolean newGraduateAllowed,
        EmploymentType employmentType
) {
}
