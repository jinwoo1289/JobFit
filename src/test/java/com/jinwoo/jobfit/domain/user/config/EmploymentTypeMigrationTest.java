package com.jinwoo.jobfit.domain.user.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 옛 단일 컬럼이 남아 있는 DB를 테스트 트랜잭션 안에서 재현해 마이그레이션을 검증한다.
 * PostgreSQL DDL은 트랜잭션에 포함되므로 테스트가 끝나면 컬럼 추가·삭제와 데이터가 모두 롤백된다.
 */
@SpringBootTest
@Transactional
class EmploymentTypeMigrationTest {

    @Autowired
    private EmploymentTypeMigration migration;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void run_movesLegacyValuesAndConvertsAnyToEmptySet() {
        jdbcTemplate.execute("ALTER TABLE user_profiles ADD COLUMN employment_type VARCHAR(30)");
        Long anyProfileId = insertLegacyProfile("ANY");
        Long internProfileId = insertLegacyProfile("INTERN");

        migration.run();

        assertThat(employmentTypesOf(anyProfileId)).isZero();
        assertThat(jdbcTemplate.queryForList(
                "SELECT employment_type FROM user_profile_employment_types WHERE user_profile_id = ?",
                String.class, internProfileId)).containsExactly("INTERN");
        assertThat(legacyColumnCount()).isZero();
    }

    @Test
    void run_withoutLegacyColumn_doesNothing() {
        assertThat(legacyColumnCount()).isZero();

        migration.run();
        migration.run();

        assertThat(legacyColumnCount()).isZero();
    }

    private Long insertLegacyProfile(String employmentType) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO user_profiles (desired_job, career_level, employment_type)
                VALUES ('마이그레이션 테스트', 'NEW_GRADUATE', ?) RETURNING id
                """, Long.class, employmentType);
    }

    private int employmentTypesOf(Long profileId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_profile_employment_types WHERE user_profile_id = ?", Integer.class, profileId);
    }

    private int legacyColumnCount() {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'user_profiles' AND column_name = 'employment_type'
                """, Integer.class);
    }
}
