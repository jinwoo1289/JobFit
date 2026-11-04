package com.jinwoo.jobfit.domain.user.config;

import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * JobCategoryCheckConstraintSynchronizer와 같은 이유로, 희망 고용형태 테이블의 CHECK 제약을 시작 시 EmploymentType 값으로 다시 만든다.
 * ddl-auto=update는 이미 있는 제약을 갱신하지 않으므로, enum에 값을 추가하면 기존 DB에서는 새 값 저장이 제약 위반으로 실패한다.
 * 기존 행의 값이 새 허용값에 없으면(enum에서 값을 뺀 경우) ADD가 실패하고 DROP까지 함께 롤백되어 제약이 사라진 채로 남지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmploymentTypeCheckConstraintSynchronizer implements CommandLineRunner {

    static final String TABLE = "user_profile_employment_types";
    static final String CONSTRAINT = "user_profile_employment_types_employment_type_check";

    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
        String allowedValues = Arrays.stream(EmploymentType.values())
                .map(type -> "'" + type.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
                + " CHECK (employment_type IN (" + allowedValues + "))");
        log.info("employment_type CHECK 제약 동기화 완료: {}", allowedValues);
    }
}
