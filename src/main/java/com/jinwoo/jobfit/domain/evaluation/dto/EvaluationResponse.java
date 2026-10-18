package com.jinwoo.jobfit.domain.evaluation.dto;

import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationVerdict;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;

import java.util.List;

public record EvaluationResponse(
        Long jobPostingId,
        String companyName,
        String title,
        String originalUrl,
        double totalScore,
        double skillScore, boolean skillExcluded,
        double experienceScore, boolean experienceExcluded,
        double preferenceScore, boolean preferenceExcluded,
        double certificateScore, boolean certificateExcluded,
        EvaluationVerdict verdict,
        List<String> failedReasons,
        EvaluationReasoning reasoning
) {
    public static EvaluationResponse from(EvaluationResult result, JobPosting jobPosting) {
        return new EvaluationResponse(
                result.jobPostingId(),
                jobPosting.getCompanyName(),
                jobPosting.getTitle(),
                jobPosting.getOriginalUrl(),
                result.totalScore(),
                result.skillScore(), result.skillExcluded(),
                result.experienceScore(), result.experienceExcluded(),
                result.preferenceScore(), result.preferenceExcluded(),
                result.certificateScore(), result.certificateExcluded(),
                result.verdict(),
                result.failedReasons(),
                result.reasoning()
        );
    }
}
