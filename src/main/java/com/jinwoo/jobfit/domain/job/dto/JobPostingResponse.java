package com.jinwoo.jobfit.domain.job.dto;

import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;

import java.time.LocalDateTime;

public record JobPostingResponse(
        Long id,
        JobSource source,
        String externalId,
        String companyName,
        String title,
        String originalUrl,
        String location,
        String experienceLevel,
        String educationLevel,
        String keywords,
        String description,
        CloseType closeType,
        LocalDateTime expirationAt,
        LocalDateTime collectedAt
) {
    public static JobPostingResponse from(JobPosting entity) {
        return new JobPostingResponse(
                entity.getId(),
                entity.getSource(),
                entity.getExternalId(),
                entity.getCompanyName(),
                entity.getTitle(),
                entity.getOriginalUrl(),
                entity.getLocation(),
                entity.getExperienceLevel(),
                entity.getEducationLevel(),
                entity.getKeywords(),
                entity.getDescription(),
                entity.getCloseType(),
                entity.getExpirationAt(),
                entity.getCollectedAt()
        );
    }
}