package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.dto.EvaluationResponse;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.RequiredSkillMatch;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.JobApplication;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.repository.EvaluationWeightRepository;
import com.jinwoo.jobfit.domain.user.repository.JobApplicationRepository;
import com.jinwoo.jobfit.domain.user.repository.UserCertificateRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProjectRepository;
import com.jinwoo.jobfit.domain.user.repository.UserSkillRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EvaluationQueryServiceTest {

    private static final Long PROFILE_ID = 1L;

    private final EvaluationService evaluationService = mock(EvaluationService.class);
    private final UserProfileRepository userProfileRepository = mock(UserProfileRepository.class);
    private final UserSkillRepository userSkillRepository = mock(UserSkillRepository.class);
    private final UserProjectRepository userProjectRepository = mock(UserProjectRepository.class);
    private final UserCertificateRepository userCertificateRepository = mock(UserCertificateRepository.class);
    private final EvaluationWeightRepository evaluationWeightRepository = mock(EvaluationWeightRepository.class);
    private final JobPostingRepository jobPostingRepository = mock(JobPostingRepository.class);
    private final JobApplicationRepository jobApplicationRepository = mock(JobApplicationRepository.class);

    private final EvaluationQueryService evaluationQueryService = new EvaluationQueryService(
            evaluationService, userProfileRepository, userSkillRepository, userProjectRepository,
            userCertificateRepository, evaluationWeightRepository, jobPostingRepository, jobApplicationRepository);

    @Test
    void evaluateAll_marksOnlyAppliedPostingsWithTheirAppliedAt() {
        JobPosting appliedPosting = jobPosting(10L);
        JobPosting notAppliedPosting = jobPosting(20L);
        LocalDateTime appliedAt = LocalDateTime.of(2026, 11, 2, 11, 40);
        JobApplication application = mock(JobApplication.class);
        when(application.getJobPosting()).thenReturn(appliedPosting);
        when(application.getAppliedAt()).thenReturn(appliedAt);

        when(userProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(mock(UserProfile.class)));
        when(evaluationWeightRepository.findByUserProfileId(PROFILE_ID)).thenReturn(Optional.of(mock(EvaluationWeight.class)));
        when(jobPostingRepository.findAll()).thenReturn(List.of(appliedPosting, notAppliedPosting));
        when(jobApplicationRepository.findByUserProfileId(PROFILE_ID)).thenReturn(List.of(application));
        when(evaluationService.evaluate(any(), anyList(), anyList(), anyList(), any(), eq(appliedPosting), any()))
                .thenReturn(fitResult(10L));
        when(evaluationService.evaluate(any(), anyList(), anyList(), anyList(), any(), eq(notAppliedPosting), any()))
                .thenReturn(fitResult(20L));

        Map<Long, EvaluationResponse> responses = evaluationQueryService.evaluateAll(PROFILE_ID).stream()
                .collect(Collectors.toMap(EvaluationResponse::jobPostingId, Function.identity()));

        assertThat(responses.get(10L).applied()).isTrue();
        assertThat(responses.get(10L).appliedAt()).isEqualTo(appliedAt);
        assertThat(responses.get(20L).applied()).isFalse();
        assertThat(responses.get(20L).appliedAt()).isNull();
    }

    private JobPosting jobPosting(Long id) {
        JobPosting jobPosting = mock(JobPosting.class);
        when(jobPosting.getId()).thenReturn(id);
        when(jobPosting.getCompanyName()).thenReturn("회사" + id);
        when(jobPosting.getTitle()).thenReturn("공고" + id);
        when(jobPosting.getOriginalUrl()).thenReturn("https://example.com/" + id);
        return jobPosting;
    }

    private EvaluationResult fitResult(Long jobPostingId) {
        ScoreResult scoreResult = new ScoreResult(100.0, false, 100.0, false, 0.0, true, 0.0, true);
        RequiredSkillMatch skillMatch = new RequiredSkillMatch(0, List.of(), List.of(), 0, 0);
        return EvaluationResult.fit(jobPostingId, 100.0, scoreResult, List.of(), EvaluationReasoning.empty(), skillMatch);
    }
}
