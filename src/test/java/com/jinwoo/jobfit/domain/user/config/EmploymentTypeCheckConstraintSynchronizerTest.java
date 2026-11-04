package com.jinwoo.jobfit.domain.user.config;

import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * enum 값이 바뀐 뒤의 기존 DB를 재현하려고, 테스트 트랜잭션 안에서 제약을 옛 허용값으로 바꿔 둔 뒤 동기화한다.
 * PostgreSQL DDL은 트랜잭션에 포함되므로 테스트가 끝나면 원래 제약으로 롤백된다.
 */
@SpringBootTest
@Transactional
class EmploymentTypeCheckConstraintSynchronizerTest {

    @Autowired
    private EmploymentTypeCheckConstraintSynchronizer synchronizer;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void run_replacesStaleConstraintWithCurrentEnumValues() {
        jdbcTemplate.execute("ALTER TABLE " + EmploymentTypeCheckConstraintSynchronizer.TABLE
                + " DROP CONSTRAINT IF EXISTS " + EmploymentTypeCheckConstraintSynchronizer.CONSTRAINT);
        jdbcTemplate.execute("ALTER TABLE " + EmploymentTypeCheckConstraintSynchronizer.TABLE
                + " ADD CONSTRAINT " + EmploymentTypeCheckConstraintSynchronizer.CONSTRAINT
                // 실제 데이터가 있는 DB에서도 옛 제약을 재현할 수 있게 기존 행 검사는 건너뛴다.
                + " CHECK (employment_type IN ('FULL_TIME', 'ANY')) NOT VALID");

        synchronizer.run();

        String definition = constraintDefinition();
        Arrays.stream(EmploymentType.values())
                .forEach(type -> assertThat(definition).contains("'" + type.name() + "'"));
        assertThat(definition).doesNotContain("'ANY'");
    }

    @Test
    void run_isIdempotent() {
        synchronizer.run();
        String first = constraintDefinition();

        synchronizer.run();

        assertThat(constraintDefinition()).isEqualTo(first);
    }

    private String constraintDefinition() {
        return jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                String.class, EmploymentTypeCheckConstraintSynchronizer.CONSTRAINT);
    }
}
