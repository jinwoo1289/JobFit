package com.jinwoo.jobfit.domain.evaluation.dto;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementReExtraction;

public record ReExtractionResponse(
        Long jobPostingId,
        JobRequirementExtraction before,
        JobRequirementExtraction after
) {
    public static ReExtractionResponse from(Long jobPostingId, JobRequirementReExtraction reExtraction) {
        return new ReExtractionResponse(jobPostingId, reExtraction.before(), reExtraction.after());
    }
}
