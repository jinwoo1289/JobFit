package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;

public record UserProfileCreateRequest(
        @NotBlank String desiredJob,
        @NotNull CareerLevel careerLevel,
        Integer yearsOfExperience,
        String desiredLocation,
        // 비우거나 생략하면 고용형태 무관
        Set<@NotNull EmploymentType> employmentTypes,
        @NotEmpty List<@Valid UserSkillRequest> skills,
        List<@Valid UserProjectRequest> projects,
        List<@Valid UserCertificateRequest> certificates,
        @NotNull @Valid EvaluationWeightRequest weights
) {
    public UserProfile toEntity() {
        return new UserProfile(desiredJob, careerLevel, yearsOfExperience, desiredLocation, employmentTypes);
    }
}
