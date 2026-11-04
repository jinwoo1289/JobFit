package com.jinwoo.jobfit.domain.user.service;

import com.jinwoo.jobfit.domain.evaluation.client.JsonSchemaFormat;
import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.dto.EvaluationResponse;
import com.jinwoo.jobfit.domain.evaluation.service.EvaluationQueryService;
import com.jinwoo.jobfit.domain.job.dto.JobPostingCreateRequest;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import com.jinwoo.jobfit.domain.job.service.JobPostingService;
import com.jinwoo.jobfit.domain.user.dto.EvaluationWeightRequest;
import com.jinwoo.jobfit.domain.user.dto.JobApplicationResponse;
import com.jinwoo.jobfit.domain.user.dto.UserCertificateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProfileCreateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProjectRequest;
import com.jinwoo.jobfit.domain.user.dto.UserSkillRequest;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.repository.JobApplicationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Set;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

// 추출·checkPoints 캐시 저장이 REQUIRES_NEW 트랜잭션에서 일어나 테스트 트랜잭션 롤백으로는 정리되지 않으므로,
// 테스트 전용 공고·프로필을 커밋해 쓰고 @AfterEach에서 직접 지운다. 시드 공고의 캐시를 건드리지 않도록
// evaluateAll은 호출하지 않는다(evaluateAll의 applied 반영은 EvaluationQueryServiceTest에서 mock으로 검증).
@SpringBootTest(properties = "jobfit.profile.edit-enabled=true")
class JobApplicationServiceTest {

    private static final String EXTRACTION_JSON = """
            {"requiredSkills": ["Java"], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
            """;
    private static final String REASONING_JSON = """
            {"matchedPoints": [], "gapPoints": [], "checkPoints": ["테스트 확인 항목"]}
            """;

    @MockitoBean
    private LlmClient llmClient;

    @Autowired
    private JobApplicationService jobApplicationService;
    @Autowired
    private JobApplicationRepository jobApplicationRepository;
    @Autowired
    private EvaluationQueryService evaluationQueryService;
    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private JobPostingService jobPostingService;
    @Autowired
    private JobPostingRepository jobPostingRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long profileId;
    private Long jobPostingId;

    @BeforeEach
    void setUp() {
        when(llmClient.complete(anyString(), anyString(), any(JsonSchemaFormat.class))).thenReturn(EXTRACTION_JSON);
        when(llmClient.complete(anyString(), anyString())).thenReturn(REASONING_JSON);

        profileId = userProfileService.create(newProfileRequest()).id();
        String externalId = "test-applied-" + UUID.randomUUID();
        jobPostingService.create(newJobPostingRequest(externalId));
        jobPostingId = jobPostingRepository.findBySourceAndExternalId(JobSource.SARAMIN, externalId).orElseThrow().getId();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("DELETE FROM job_applications WHERE job_posting_id = ?", jobPostingId);
        jdbcTemplate.update("DELETE FROM evaluation_reasoning_caches WHERE job_posting_id = ? OR user_profile_id = ?",
                jobPostingId, profileId);
        jdbcTemplate.update("DELETE FROM job_requirement_extractions WHERE job_posting_id = ?", jobPostingId);
        jdbcTemplate.update("DELETE FROM job_postings WHERE id = ?", jobPostingId);
        userProfileService.delete(profileId);
    }

    @Test
    void markApplied_twice_keepsSingleRowAndOriginalAppliedAt_thenUnmarkDeletes() {
        JobApplicationResponse first = jobApplicationService.markApplied(profileId, jobPostingId);
        assertThat(countApplications()).isEqualTo(1);
        assertThat(first.appliedAt()).isNotNull();

        JobApplicationResponse second = jobApplicationService.markApplied(profileId, jobPostingId);
        assertThat(countApplications()).isEqualTo(1);
        assertThat(second.appliedAt()).isEqualTo(first.appliedAt());

        jobApplicationService.unmarkApplied(profileId, jobPostingId);
        assertThat(countApplications()).isZero();

        // 체크되지 않은 상태에서 다시 해제해도 오류 없이 끝난다.
        jobApplicationService.unmarkApplied(profileId, jobPostingId);
        assertThat(countApplications()).isZero();
    }

    @Test
    void evaluate_reflectsAppliedWithoutCallingLlmAgain() {
        // 첫 평가로 추출·checkPoints 캐시를 채운다(여기서는 mock LLM이 호출된다).
        EvaluationResponse before = evaluationQueryService.evaluateOne(profileId, jobPostingId);
        assertThat(before.applied()).isFalse();
        assertThat(before.appliedAt()).isNull();
        Mockito.clearInvocations(llmClient);

        JobApplicationResponse applied = jobApplicationService.markApplied(profileId, jobPostingId);

        EvaluationResponse afterOne = evaluationQueryService.evaluateOne(profileId, jobPostingId);
        assertThat(afterOne.applied()).isTrue();
        assertThat(afterOne.appliedAt()).isEqualTo(applied.appliedAt());
        // 평가 결과(근거 포함)는 지원 여부와 무관하게 그대로다.
        assertThat(afterOne.reasoning()).isEqualTo(before.reasoning());
        assertThat(afterOne.totalScore()).isEqualTo(before.totalScore());

        jobApplicationService.unmarkApplied(profileId, jobPostingId);
        EvaluationResponse afterUnmark = evaluationQueryService.evaluateOne(profileId, jobPostingId);
        assertThat(afterUnmark.applied()).isFalse();

        // 캐시가 채워진 뒤에는 체크·해제 후 평가해도 LLM을 다시 부르지 않는다.
        verifyNoInteractions(llmClient);
    }

    private int countApplications() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM job_applications WHERE user_profile_id = ? AND job_posting_id = ?",
                Integer.class, profileId, jobPostingId);
        return count == null ? 0 : count;
    }

    private UserProfileCreateRequest newProfileRequest() {
        return new UserProfileCreateRequest(
                "백엔드 개발자",
                CareerLevel.NEW_GRADUATE,
                null,
                "서울",
                Set.of(EmploymentType.FULL_TIME),
                List.of(new UserSkillRequest("Java")),
                List.of(new UserProjectRequest("JobFit", "채용 적합도 서비스", List.of("Java"))),
                List.of(new UserCertificateRequest("OPIc", "IH", LocalDate.of(2024, 3, 15))),
                new EvaluationWeightRequest(40, 30, 20, 10)
        );
    }

    private JobPostingCreateRequest newJobPostingRequest(String externalId) {
        return new JobPostingCreateRequest(
                JobSource.SARAMIN,
                externalId,
                "테스트회사",
                "지원 완료 테스트 공고",
                "https://example.com/" + externalId,
                "서울",
                "신입",
                "학력무관",
                "Java",
                "Java 백엔드 개발자를 모집합니다.",
                CloseType.ALWAYS_OPEN,
                null
        );
    }
}
