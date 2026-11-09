package com.jinwoo.jobfit.domain.evaluation.vo;

import com.jinwoo.jobfit.domain.user.entity.EmploymentType;

public record JobRequirement(
        Integer minYears,
        Integer maxYears,
        ExperienceLevelType experienceLevelType,
        EmploymentType employmentType
) {

    // 판정 불가(UNKNOWN)는 신입 지원을 막을 근거가 없으므로 허용으로 본다. 경력 전용으로 명시된 경우만 false다.
    public boolean newGraduateAllowed() {
        return experienceLevelType != ExperienceLevelType.EXPERIENCED_ONLY;
    }
}
