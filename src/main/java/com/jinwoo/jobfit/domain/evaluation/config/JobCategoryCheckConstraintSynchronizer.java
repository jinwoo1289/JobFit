package com.jinwoo.jobfit.domain.evaluation.config;

import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Hibernate는 EnumType.STRING 컬럼에 허용값 CHECK 제약을 만들지만, ddl-auto=update는 이미 있는 제약을 갱신하지 않는다.
 * 그래서 JobCategory에 값을 추가하면 기존 DB에서는 새 값 INSERT/UPDATE가 제약 위반으로 실패한다.
 * 별도 마이그레이션 도구 없이 ddl-auto=update로 스키마를 관리하므로, 시작 시 제약을 현재 enum 값으로 다시 만든다.
 * 기존 행의 값은 모두 새 허용값에 포함되므로 데이터는 변경되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobCategoryCheckConstraintSynchronizer implements CommandLineRunner {

    private static final String TABLE = "job_requirement_extractions";
    private static final String CONSTRAINT = "job_requirement_extractions_job_category_check";

    private final JdbcTemplate jdbcTemplate;

    // PostgreSQL DDL은 트랜잭션 안에서 실행되므로, ADD가 실패하면 DROP도 롤백되어 제약이 사라진 채로 남지 않는다.
    @Override
    @Transactional
    public void run(String... args) {
        String allowedValues = Arrays.stream(JobCategory.values())
                .map(category -> "'" + category.name() + "'")
                .collect(Collectors.joining(", "));

        jdbcTemplate.execute("ALTER TABLE " + TABLE + " DROP CONSTRAINT IF EXISTS " + CONSTRAINT);
        jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD CONSTRAINT " + CONSTRAINT
                + " CHECK (job_category IN (" + allowedValues + "))");
        log.info("job_category CHECK 제약 동기화 완료: {}", allowedValues);
    }
}
