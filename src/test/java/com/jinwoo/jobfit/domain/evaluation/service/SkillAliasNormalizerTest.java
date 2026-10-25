package com.jinwoo.jobfit.domain.evaluation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SkillAliasNormalizerTest {

    private final SkillAliasNormalizer normalizer = new SkillAliasNormalizer();

    @Test
    void normalize_api_toRestApi() {
        assertThat(normalizer.normalize("API")).isEqualTo("rest api");
    }

    @Test
    void matches_api_withUserRestApi() {
        Set<String> userSkills = Set.of(normalizer.normalize("REST API"));

        assertThat(normalizer.matches(normalizer.normalize("API"), userSkills)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RDB", "Relational Database"})
    void matches_rdbAliases_withUserMysqlOrPostgresql(String jobSkill) {
        String normalized = normalizer.normalize(jobSkill);

        assertThat(normalizer.isCategoryAlias(normalized)).isTrue();
        assertThat(normalizer.matches(normalized, Set.of("mysql"))).isTrue();
        assertThat(normalizer.matches(normalized, Set.of("postgresql"))).isTrue();
        assertThat(normalizer.matches(normalized, Set.of("redis"))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NoSQL", "Cache DB"})
    void matches_nosqlAliases_withUserRedisOrMongodb(String jobSkill) {
        String normalized = normalizer.normalize(jobSkill);

        assertThat(normalizer.isCategoryAlias(normalized)).isTrue();
        assertThat(normalizer.matches(normalized, Set.of("redis"))).isTrue();
        assertThat(normalizer.matches(normalized, Set.of("mongodb"))).isTrue();
        assertThat(normalizer.matches(normalized, Set.of("mysql"))).isFalse();
    }
}
