package com.jinwoo.jobfit.domain.evaluation.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 공고가 사용하는 기술 표기와 사용자 프로필의 표기가 달라도 같은 기술로 인식하도록 정규화한다.
 * 예: "Spring"/"Spring Framework" → "Spring Boot".
 * 범주형 별칭(예: "AI Coding Assistant")은 특정 제품명이 아니라 카테고리 표현이므로, 사용자가 그
 * 범주에 속하는 구체적인 제품(Claude Code, Copilot, Cursor 등) 중 하나라도 보유하면 일치로 처리한다.
 */
@Component
public class SkillAliasNormalizer {

    private static final Map<String, String> ALIASES = Map.of(
            "spring framework", "spring boot",
            "spring", "spring boot"
    );

    private record CategoryAlias(Set<String> aliasTerms, Set<String> equivalentSkills) {
        boolean isAlias(String normalizedSkill) {
            return aliasTerms.contains(normalizedSkill);
        }

        boolean matches(Set<String> normalizedUserSkills) {
            return normalizedUserSkills.stream()
                    .anyMatch(userSkill -> equivalentSkills.stream().anyMatch(userSkill::contains));
        }
    }

    private static final List<CategoryAlias> CATEGORY_ALIASES = List.of(
            new CategoryAlias(
                    Set.of("rdbms", "sql", "rdbms/sql"),
                    Set.of("mysql", "postgresql")
            ),
            new CategoryAlias(
                    Set.of("ai coding assistant", "generative ai", "ai 코딩 도구"),
                    Set.of("claude code", "copilot", "cursor")
            )
    );

    public String normalize(String skill) {
        String key = skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(key, key);
    }

    public boolean isCategoryAlias(String normalizedSkill) {
        return CATEGORY_ALIASES.stream().anyMatch(category -> category.isAlias(normalizedSkill));
    }

    public boolean matchesCategoryEquivalent(String normalizedSkill, Set<String> normalizedUserSkills) {
        return CATEGORY_ALIASES.stream()
                .filter(category -> category.isAlias(normalizedSkill))
                .anyMatch(category -> category.matches(normalizedUserSkills));
    }

    // 정규화된 기술명이 정규화된 후보 목록(보유 스킬, 프로젝트 기술 스택 등)과 일치하는지 범주 별칭까지
    // 고려해 판정한다. 스킬 점수·경험 점수·근거 문구 생성이 모두 동일한 기준으로 매칭하도록 공유된다.
    public boolean matches(String normalizedSkill, Set<String> normalizedCandidates) {
        if (isCategoryAlias(normalizedSkill)) {
            return matchesCategoryEquivalent(normalizedSkill, normalizedCandidates);
        }
        return normalizedCandidates.contains(normalizedSkill);
    }
}
