package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import jakarta.validation.constraints.NotBlank;

public record UserSkillRequest(
        @NotBlank String skillName
) {
    public UserSkill toEntity(UserProfile userProfile) {
        return new UserSkill(userProfile, skillName);
    }
}
