package com.jinwoo.jobfit.domain.job.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "job_postings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_job_posting_source_external_id",
                        columnNames = {"source", "external_id"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPosting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private JobSource source;

    @Column(name = "external_id", nullable = false, length = 100)
    private String externalId;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "original_url", nullable = false, length = 1000)
    private String originalUrl;

    @Column(length = 200)
    private String location;

    @Column(name = "experience_level", length = 100)
    private String experienceLevel;

    @Column(name = "education_level", length = 100)
    private String educationLevel;

    @Column(length = 1000)
    private String keywords;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "close_type", length = 30)
    private CloseType closeType;

    @Column(name = "expiration_at")
    private LocalDateTime expirationAt;

    @Column(name = "collected_at", nullable = false, updatable = false)
    private LocalDateTime collectedAt;


    public JobPosting(JobSource source,
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
                      LocalDateTime expirationAt) {
        this.source = source;
        this.externalId = externalId;
        this.companyName = companyName;
        this.title = title;
        this.originalUrl = originalUrl;
        this.location = location;
        this.experienceLevel = experienceLevel;
        this.educationLevel = educationLevel;
        this.keywords = keywords;
        this.description = description;
        this.closeType = closeType;
        this.expirationAt = expirationAt;
    }

    @PrePersist
    private void prePersist() {
        if (collectedAt == null) {
            collectedAt = LocalDateTime.now();
        }
    }
}
