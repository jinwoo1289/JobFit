package com.jinwoo.jobfit.domain.evaluation.vo;

// before는 재추출 전 캐시 값이며, 캐시가 없던 공고는 null이다.
public record JobRequirementReExtraction(
        JobRequirementExtraction before,
        JobRequirementExtraction after
) {
}
