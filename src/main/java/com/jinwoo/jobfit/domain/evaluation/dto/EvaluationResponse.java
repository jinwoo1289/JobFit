package com.jinwoo.jobfit.domain.evaluation.dto;

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
        EvaluationVerdict verdict,
        List<String> failedReasons
) {
    public static EvaluationResponse from(EvaluationResult result, JobPosting jobPosting) {
        return new EvaluationResponse(
                result.jobPostingId(),
                jobPosting.getCompanyName(),
                jobPosting.getTitle(),
                result.totalScore(),
                result.skillScore(),
                result.experienceScore(),
                result.verdict(),
                result.failedReasons()
        );
    }
}
