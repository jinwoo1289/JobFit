package com.jinwoo.jobfit.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 기존 프로필 수정 요청. 가중치는 PUT .../weights에서 따로 다루므로 받지 않는다.
 * 보유 기술은 프로젝트 기술 스택과 같은 규칙(trim·lowercase·빈 값/중복 제거)으로만 정규화하고,
 * 별칭 치환(SkillAliasNormalizer)은 매칭 시점에만 적용해 사용자가 입력한 기술명을 보존한다.
 * 전역 설정은 모르는 필드를 무시하지만, 이 요청은 옛 필드(employmentType 단일값)가 조용히 버려져 "무관"으로
 * 저장되는 일을 막기 위해 모르는 필드가 오면 역직렬화를 실패시켜 400으로 응답한다.
 */
public record UserProfileUpdateRequest(
        @NotBlank String desiredJob,
        @NotNull CareerLevel careerLevel,
        @PositiveOrZero Integer yearsOfExperience,
        String desiredLocation,
        // 비우거나 생략하면 고용형태 무관
        Set<@NotNull EmploymentType> employmentTypes,
        @NotNull List<String> skills,
        List<@Valid UserProjectRequest> projects,
        List<@Valid UserCertificateRequest> certificates
) {
    @JsonAnySetter
    void rejectUnknownField(String name, Object value) {
        throw new IllegalArgumentException("알 수 없는 필드입니다: " + name);
    }

    public List<String> normalizedSkills() {
        if (skills == null) {
            return List.of();
        }
        return skills.stream()
                .filter(Objects::nonNull)
                .map(skill -> skill.trim().toLowerCase(Locale.ROOT))
                .filter(skill -> !skill.isBlank())
                .distinct()
                .toList();
    }

    @AssertTrue(message = "보유 기술을 1개 이상 입력해야 합니다.")
    public boolean isSkillsPresent() {
        return !normalizedSkills().isEmpty();
    }

    public List<UserSkill> toSkillEntities(UserProfile userProfile) {
        return normalizedSkills().stream()
                .map(skill -> new UserSkill(userProfile, skill))
                .toList();
    }
}
