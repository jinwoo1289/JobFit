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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Entity
@Table(name = "job_requirement_extractions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobRequirementExtractionEntity {

    private static final String GROUP_MEMBER_DELIMITER = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false, unique = true)
    private JobPosting jobPosting;

    @Column(name = "required_skills", length = 1000)
    private String requiredSkills;

    // 그룹은 ","로, 그룹 안의 기술은 "|"로 구분한다. 예: "Linux|Windows,Java|Kotlin"
    @Column(name = "required_any_of", length = 1000)
    private String requiredAnyOf;

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

    @Column(name = "preferred_certificates", length = 1000)
    private String preferredCertificates;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_category", length = 30)
    private JobCategory jobCategory;

    // 이 결과를 만든 추출 프롬프트 버전. 컬럼 추가 이전에 저장된 행은 null이며, 현재 버전과 다르면 재추출 대상이다.
    @Column(name = "prompt_version")
    private Integer promptVersion;

    public JobRequirementExtractionEntity(JobPosting jobPosting, JobRequirementExtraction extraction, int promptVersion) {
        this.jobPosting = jobPosting;
        overwrite(extraction, promptVersion);
    }

    // 재추출 결과로 같은 행을 갱신한다. job_posting_id 유니크 제약이 있어 새 행을 INSERT하면 충돌하기 때문이다.
    public void overwrite(JobRequirementExtraction extraction, int promptVersion) {
        this.requiredSkills = join(extraction.requiredSkills());
        this.requiredAnyOf = joinGroups(extraction.requiredAnyOf());
        this.preferredSkills = join(extraction.preferredSkills());
        this.minYears = extraction.minYears();
        this.maxYears = extraction.maxYears();
        this.newGraduateAllowed = extraction.newGraduateAllowed();
        this.requiredCertificates = join(extraction.requiredCertificates());
        this.preferredCertificates = join(extraction.preferredCertificates());
        this.jobCategory = extraction.jobCategory();
        this.promptVersion = promptVersion;
    }

    public boolean isExtractedWith(int promptVersion) {
        return this.promptVersion != null && this.promptVersion == promptVersion;
    }

    public JobRequirementExtraction toExtraction() {
        return new JobRequirementExtraction(
                split(requiredSkills),
                splitGroups(requiredAnyOf),
                split(preferredSkills),
                minYears,
                maxYears,
                newGraduateAllowed,
                split(requiredCertificates),
                split(preferredCertificates),
                jobCategory
        );
    }

    private static String join(List<String> values) {
        return String.join(",", values);
    }

    private static String joinGroups(List<List<String>> groups) {
        return groups.stream()
                .map(group -> String.join(GROUP_MEMBER_DELIMITER, group))
                .collect(Collectors.joining(","));
    }

    // 컬럼 추가 이전에 저장된 행은 null이며, 택1 그룹이 없는 것으로 읽는다.
    private static List<List<String>> splitGroups(String value) {
        return split(value).stream()
                .map(group -> Arrays.stream(group.split(Pattern.quote(GROUP_MEMBER_DELIMITER)))
                        .map(String::trim)
                        .filter(member -> !member.isEmpty())
                        .toList())
                .filter(group -> !group.isEmpty())
                .toList();
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
