package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;

import java.util.List;

public record UserProfileResponse(
        Long id,
        String desiredJob,
        CareerLevel careerLevel,
        Integer yearsOfExperience,
        String desiredLocation,
        EmploymentType employmentType,
        List<UserSkillResponse> skills,
        List<UserProjectResponse> projects,
        List<UserCertificateResponse> certificates,
        EvaluationWeightResponse weights
) {
    public static UserProfileResponse from(UserProfile profile,
                                            List<UserSkill> skills,
                                            List<UserProject> projects,
                                            List<UserCertificate> certificates,
                                            EvaluationWeight weight) {
        return new UserProfileResponse(
                profile.getId(),
                profile.getDesiredJob(),
                profile.getCareerLevel(),
                profile.getYearsOfExperience(),
                profile.getDesiredLocation(),
                profile.getEmploymentType(),
                skills.stream().map(UserSkillResponse::from).toList(),
                projects.stream().map(UserProjectResponse::from).toList(),
                certificates.stream().map(UserCertificateResponse::from).toList(),
                EvaluationWeightResponse.from(weight)
        );
    }
}
