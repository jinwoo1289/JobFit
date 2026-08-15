package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

public record JobRequirementExtraction(
        List<String> requiredSkills,
        List<String> preferredSkills,
        Integer minYears,
        Integer maxYears,
        boolean newGraduateAllowed,
        List<String> requiredCertificates,
        JobCategory jobCategory
) {
}
