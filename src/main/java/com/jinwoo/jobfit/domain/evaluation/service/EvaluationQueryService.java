package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.dto.EvaluationResponse;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.exception.JobPostingNotFoundException;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.JobApplication;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import com.jinwoo.jobfit.domain.user.exception.UserProfileNotFoundException;
import com.jinwoo.jobfit.domain.user.repository.EvaluationWeightRepository;
import com.jinwoo.jobfit.domain.user.repository.JobApplicationRepository;
import com.jinwoo.jobfit.domain.user.repository.UserCertificateRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProjectRepository;
import com.jinwoo.jobfit.domain.user.repository.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationQueryService {

    private final EvaluationService evaluationService;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserProjectRepository userProjectRepository;
    private final UserCertificateRepository userCertificateRepository;
    private final EvaluationWeightRepository evaluationWeightRepository;
    private final JobPostingRepository jobPostingRepository;
    private final JobApplicationRepository jobApplicationRepository;

    public List<EvaluationResponse> evaluateAll(Long userProfileId) {
        UserProfile userProfile = getUserProfile(userProfileId);
        List<UserSkill> skills = userSkillRepository.findByUserProfileId(userProfileId);
        List<UserProject> projects = userProjectRepository.findByUserProfileId(userProfileId);
        List<UserCertificate> certificates = userCertificateRepository.findByUserProfileId(userProfileId);
        EvaluationWeight evaluationWeight = getEvaluationWeight(userProfileId);
        LlmCallBreaker breaker = new LlmCallBreaker();
        Map<Long, LocalDateTime> appliedAtByJobPostingId = jobApplicationRepository.findByUserProfileId(userProfileId).stream()
                .collect(Collectors.toMap(application -> application.getJobPosting().getId(), JobApplication::getAppliedAt));

        return jobPostingRepository.findAll().stream()
                .map(jobPosting -> evaluate(userProfile, skills, projects, certificates, evaluationWeight, jobPosting, breaker,
                        appliedAtByJobPostingId.get(jobPosting.getId())))
                .sorted(Comparator.comparingDouble(EvaluationResponse::totalScore).reversed())
                .toList();
    }

    public EvaluationResponse evaluateOne(Long userProfileId, Long jobPostingId) {
        UserProfile userProfile = getUserProfile(userProfileId);
        List<UserSkill> skills = userSkillRepository.findByUserProfileId(userProfileId);
        List<UserProject> projects = userProjectRepository.findByUserProfileId(userProfileId);
        List<UserCertificate> certificates = userCertificateRepository.findByUserProfileId(userProfileId);
        EvaluationWeight evaluationWeight = getEvaluationWeight(userProfileId);
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("공고를 찾을 수 없습니다. id=" + jobPostingId));

        LocalDateTime appliedAt = jobApplicationRepository.findByUserProfileIdAndJobPostingId(userProfileId, jobPostingId)
                .map(JobApplication::getAppliedAt)
                .orElse(null);

        return evaluate(userProfile, skills, projects, certificates, evaluationWeight, jobPosting, new LlmCallBreaker(), appliedAt);
    }

    private EvaluationResponse evaluate(UserProfile userProfile,
                                         List<UserSkill> skills,
                                         List<UserProject> projects,
                                         List<UserCertificate> certificates,
                                         EvaluationWeight evaluationWeight,
                                         JobPosting jobPosting,
                                         LlmCallBreaker breaker,
                                         LocalDateTime appliedAt) {
        // 지원 여부는 평가(추출·근거 생성·캐시 키)에 넘기지 않고 응답에만 붙인다. 체크를 바꿔도 LLM 재호출이 일어나지 않게 하기 위함.
        EvaluationResult result = evaluationService.evaluate(
                userProfile, skills, projects, certificates, evaluationWeight, jobPosting, breaker);
        return EvaluationResponse.from(result, jobPosting, appliedAt);
    }

    private UserProfile getUserProfile(Long id) {
        return userProfileRepository.findById(id)
                .orElseThrow(() -> new UserProfileNotFoundException("프로필을 찾을 수 없습니다. id=" + id));
    }

    private EvaluationWeight getEvaluationWeight(Long id) {
        return evaluationWeightRepository.findByUserProfileId(id)
                .orElseThrow(() -> new UserProfileNotFoundException("프로필을 찾을 수 없습니다. id=" + id));
    }
}
