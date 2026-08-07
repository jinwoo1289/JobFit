package com.jinwoo.jobfit.domain.user.dto;

import com.jinwoo.jobfit.domain.user.entity.UserSkill;

public record UserSkillResponse(
        Long id,
        String skillName
) {
    public static UserSkillResponse from(UserSkill entity) {
        return new UserSkillResponse(entity.getId(), entity.getSkillName());
    }
}
