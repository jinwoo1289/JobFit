package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UserProfileCreateRequest(
        @NotBlank String desiredJob,
        @NotNull CareerLevel careerLevel,
        Integer yearsOfExperience,
        String desiredLocation,
        @NotNull EmploymentType employmentType,
        @NotEmpty List<@Valid UserSkillRequest> skills,
        List<@Valid UserProjectRequest> projects,
        List<@Valid UserCertificateRequest> certificates,
        @NotNull @Valid EvaluationWeightRequest weights
) {
    public UserProfile toEntity() {
        return new UserProfile(desiredJob, careerLevel, yearsOfExperience, desiredLocation, employmentType);
    }
}
