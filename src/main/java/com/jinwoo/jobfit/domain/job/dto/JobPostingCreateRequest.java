package com.jinwoo.jobfit.domain.job.dto;

import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record JobPostingCreateRequest(
        @NotNull JobSource source,
        @NotBlank String externalId,
        @NotBlank String companyName,
        @NotBlank String title,
        @NotBlank String originalUrl,
        String location,
        String experienceLevel,
        String educationLevel,
        String keywords,
        String description,
        CloseType closeType,
        LocalDateTime expirationAt
) {
    public JobPosting toEntity() {
        return new JobPosting(
                source,
                externalId,
                companyName,
                title,
                originalUrl,
                location,
                experienceLevel,
                educationLevel,
                keywords,
                description,
                closeType,
                expirationAt
        );
    }
}