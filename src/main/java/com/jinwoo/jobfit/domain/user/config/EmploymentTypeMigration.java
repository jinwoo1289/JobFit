package com.jinwoo.jobfit.domain.user.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 희망 고용형태를 단일 컬럼(user_profiles.employment_type)에서 복수 선택 테이블(user_profile_employment_types)로 옮긴다.
 * 마이그레이션 도구 없이 ddl-auto=update로 스키마를 관리하는데, update는 새 테이블만 만들고 옛 NOT NULL 컬럼은 남겨 두므로
 * 그대로 두면 프로필 INSERT가 실패한다. 시작 시 옛 컬럼이 남아 있으면 값을 옮기고 컬럼을 지운다.
 * - ANY(무관)는 빈 집합으로 바꾼다. 새 모델에서는 "아무것도 선택 안 함 = 무관"이다.
 * - 옛 컬럼이 없으면(이미 옮겼거나 새 DB) 아무것도 하지 않으므로 여러 번 실행해도 안전하다.
 * - PostgreSQL DDL은 트랜잭션 안에서 실행되므로, 중간에 실패하면 복사와 컬럼 삭제가 함께 롤백된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmploymentTypeMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        if (!legacyColumnExists()) {
            return;
        }

        int copied = jdbcTemplate.update("""
                INSERT INTO user_profile_employment_types (user_profile_id, employment_type)
                SELECT p.id, p.employment_type
                FROM user_profiles p
                WHERE p.employment_type IS NOT NULL
                  AND p.employment_type <> 'ANY'
                  AND NOT EXISTS (
                      SELECT 1 FROM user_profile_employment_types t
                      WHERE t.user_profile_id = p.id AND t.employment_type = p.employment_type
                  )
                """);
        // 컬럼과 함께 ANY를 허용하던 CHECK 제약도 삭제된다.
        jdbcTemplate.execute("ALTER TABLE user_profiles DROP COLUMN employment_type");
        log.info("희망 고용형태 마이그레이션 완료: {}개 프로필의 값을 복수 선택 테이블로 옮김(ANY는 빈 집합으로 변환)", copied);
    }

    private boolean legacyColumnExists() {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'user_profiles'
                  AND column_name = 'employment_type'
                """, Integer.class);
        return count != null && count > 0;
    }
}
