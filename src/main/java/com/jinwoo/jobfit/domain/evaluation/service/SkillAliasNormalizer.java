package com.jinwoo.jobfit.domain.evaluation.service;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 공고가 사용하는 기술 표기와 사용자 프로필의 표기가 달라도 같은 기술로 인식하도록 정규화한다.
 * 예: "Spring Framework" → "Spring Boot", "RDBMS"/"SQL"/"RDBMS/SQL" → MySQL 또는 PostgreSQL 보유 시 일치.
 */
@Component
public class SkillAliasNormalizer {

    private static final Map<String, String> ALIASES = Map.of(
            "spring framework", "spring boot"
    );

    private static final Set<String> RDBMS_ALIASES = Set.of("rdbms", "sql", "rdbms/sql");

    private static final Set<String> RDBMS_EQUIVALENT_SKILLS = Set.of("mysql", "postgresql");

    public String normalize(String skill) {
        String key = skill == null ? "" : skill.trim().toLowerCase(Locale.ROOT);
        return ALIASES.getOrDefault(key, key);
    }

    public boolean isRdbmsAlias(String normalizedSkill) {
        return RDBMS_ALIASES.contains(normalizedSkill);
    }

    public boolean containsRdbmsEquivalent(Set<String> normalizedUserSkills) {
        return normalizedUserSkills.stream().anyMatch(RDBMS_EQUIVALENT_SKILLS::contains);
    }
}
