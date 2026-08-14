package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.dto.EvaluationResponse;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.exception.JobPostingNotFoundException;
import com.jinwoo.jobfit.domain.job.repository.JobPostingRepository;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import com.jinwoo.jobfit.domain.user.exception.UserProfileNotFoundException;
import com.jinwoo.jobfit.domain.user.repository.EvaluationWeightRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProjectRepository;
import com.jinwoo.jobfit.domain.user.repository.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EvaluationQueryService {

    private final EvaluationService evaluationService;
    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserProjectRepository userProjectRepository;
    private final EvaluationWeightRepository evaluationWeightRepository;
    private final JobPostingRepository jobPostingRepository;

    public List<EvaluationResponse> evaluateAll(Long userProfileId) {
        UserProfile userProfile = getUserProfile(userProfileId);
        List<UserSkill> skills = userSkillRepository.findByUserProfileId(userProfileId);
        List<UserProject> projects = userProjectRepository.findByUserProfileId(userProfileId);
        EvaluationWeight evaluationWeight = getEvaluationWeight(userProfileId);

        return jobPostingRepository.findAll().stream()
                .map(jobPosting -> evaluate(userProfile, skills, projects, evaluationWeight, jobPosting))
                .sorted(Comparator.comparingDouble(EvaluationResponse::totalScore).reversed())
                .toList();
    }

    public EvaluationResponse evaluateOne(Long userProfileId, Long jobPostingId) {
        UserProfile userProfile = getUserProfile(userProfileId);
        List<UserSkill> skills = userSkillRepository.findByUserProfileId(userProfileId);
        List<UserProject> projects = userProjectRepository.findByUserProfileId(userProfileId);
        EvaluationWeight evaluationWeight = getEvaluationWeight(userProfileId);
        JobPosting jobPosting = jobPostingRepository.findById(jobPostingId)
                .orElseThrow(() -> new JobPostingNotFoundException("공고를 찾을 수 없습니다. id=" + jobPostingId));

        return evaluate(userProfile, skills, projects, evaluationWeight, jobPosting);
    }

    private EvaluationResponse evaluate(UserProfile userProfile,
                                         List<UserSkill> skills,
                                         List<UserProject> projects,
                                         EvaluationWeight evaluationWeight,
                                         JobPosting jobPosting) {
        EvaluationResult result = evaluationService.evaluate(userProfile, skills, projects, evaluationWeight, jobPosting);
        return EvaluationResponse.from(result, jobPosting);
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
