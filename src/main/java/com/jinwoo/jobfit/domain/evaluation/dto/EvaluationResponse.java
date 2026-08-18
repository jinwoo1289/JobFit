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
        double totalScore,
        double skillScore,
        double experienceScore,
        double preferenceScore,
        double certificateScore,
        EvaluationVerdict verdict,
        List<String> failedReasons,
        EvaluationReasoning reasoning
) {
    public static EvaluationResponse from(EvaluationResult result, JobPosting jobPosting) {
        return new EvaluationResponse(
                result.jobPostingId(),
                jobPosting.getCompanyName(),
                jobPosting.getTitle(),
                result.totalScore(),
                result.skillScore(),
                result.experienceScore(),
                result.preferenceScore(),
                result.certificateScore(),
                result.verdict(),
                result.failedReasons(),
                result.reasoning()
        );
    }
}
