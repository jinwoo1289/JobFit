package com.jinwoo.jobfit.domain.evaluation.entity;

import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "job_requirement_extractions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirementExtractionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false, unique = true)
    private JobPosting jobPosting;

    @Column(name = "required_skills", length = 1000)
    private String requiredSkills;

    @Column(name = "preferred_skills", length = 1000)
    private String preferredSkills;

    @Column(name = "min_years")
    private Integer minYears;

    @Column(name = "max_years")
    private Integer maxYears;

    @Column(name = "new_graduate_allowed", nullable = false)
    private boolean newGraduateAllowed;

    @Column(name = "required_certificates", length = 1000)
    private String requiredCertificates;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", length = 30)
    private JobCategory jobCategory;

    public JobRequirementExtractionEntity(JobPosting jobPosting, JobRequirementExtraction extraction) {
        this.jobPosting = jobPosting;
        this.requiredSkills = join(extraction.requiredSkills());
        this.preferredSkills = join(extraction.preferredSkills());
        this.minYears = extraction.minYears();
        this.maxYears = extraction.maxYears();
        this.newGraduateAllowed = extraction.newGraduateAllowed();
        this.requiredCertificates = join(extraction.requiredCertificates());
        this.jobCategory = extraction.jobCategory();
    }

    public JobRequirementExtraction toExtraction() {
        return new JobRequirementExtraction(
                split(requiredSkills),
                split(preferredSkills),
                minYears,
                maxYears,
                newGraduateAllowed,
                split(requiredCertificates),
                jobCategory
        );
    }

    private static String join(List<String> values) {
        return String.join(",", values);
    }

    private static List<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
