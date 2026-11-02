package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.JobApplication;

import java.time.LocalDateTime;

public record JobApplicationResponse(
        Long jobPostingId,
        LocalDateTime appliedAt
) {
    public static JobApplicationResponse from(JobApplication jobApplication) {
        return new JobApplicationResponse(jobApplication.getJobPosting().getId(), jobApplication.getAppliedAt());
    }
}
