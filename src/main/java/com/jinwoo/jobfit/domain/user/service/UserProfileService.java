package com.jinwoo.jobfit.domain.user.service;

import com.jinwoo.jobfit.domain.user.dto.EvaluationWeightResponse;
import com.jinwoo.jobfit.domain.user.dto.EvaluationWeightUpdateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProfileCreateRequest;
import com.jinwoo.jobfit.domain.user.dto.UserProfileResponse;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import com.jinwoo.jobfit.domain.user.exception.UserProfileNotFoundException;
import com.jinwoo.jobfit.domain.user.repository.EvaluationWeightRepository;
import com.jinwoo.jobfit.domain.user.repository.UserCertificateRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProfileRepository;
import com.jinwoo.jobfit.domain.user.repository.UserProjectRepository;
import com.jinwoo.jobfit.domain.user.repository.UserSkillRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserProfileRepository userProfileRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserProjectRepository userProjectRepository;
    private final UserCertificateRepository userCertificateRepository;
    private final EvaluationWeightRepository evaluationWeightRepository;

    @Transactional
    public UserProfileResponse create(UserProfileCreateRequest request) {
        UserProfile userProfile = userProfileRepository.save(request.toEntity());

        List<UserSkill> skills = userSkillRepository.saveAll(
                request.skills().stream()
                        .map(skill -> skill.toEntity(userProfile))
                        .toList()
        );

        List<UserProject> projects = request.projects() == null
                ? List.of()
                : userProjectRepository.saveAll(
                        request.projects().stream()
                                .map(project -> project.toEntity(userProfile))
                                .toList()
                );

        List<UserCertificate> certificates = request.certificates() == null
                ? List.of()
                : userCertificateRepository.saveAll(
                        request.certificates().stream()
                                .map(certificate -> certificate.toEntity(userProfile))
                                .toList()
                );

        EvaluationWeight evaluationWeight = evaluationWeightRepository.save(request.weights().toEntity(userProfile));

        return UserProfileResponse.from(userProfile, skills, projects, certificates, evaluationWeight);
    }

    public UserProfileResponse findById(Long id) {
        UserProfile userProfile = getUserProfile(id);
        List<UserSkill> skills = userSkillRepository.findByUserProfileId(id);
        List<UserProject> projects = userProjectRepository.findByUserProfileId(id);
        List<UserCertificate> certificates = userCertificateRepository.findByUserProfileId(id);
        EvaluationWeight evaluationWeight = getEvaluationWeight(id);

        return UserProfileResponse.from(userProfile, skills, projects, certificates, evaluationWeight);
    }

    @Transactional
    public EvaluationWeightResponse updateWeights(Long id, EvaluationWeightUpdateRequest request) {
        getUserProfile(id);
        EvaluationWeight evaluationWeight = getEvaluationWeight(id);

        evaluationWeight.changeWeights(
                request.skillWeight(),
                request.experienceWeight(),
                request.preferenceWeight(),
                request.certificateWeight()
        );

        return EvaluationWeightResponse.from(evaluationWeight);
    }

    @Transactional
    public void delete(Long id) {
        UserProfile userProfile = getUserProfile(id);
        userProfileRepository.delete(userProfile);
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
