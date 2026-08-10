package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class JobRequirementParserTest {

    private final JobRequirementParser parser = new JobRequirementParser();

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("seedExperienceLevels")
    void parse_seedExperienceLevel(String title,
                                    String experienceLevel,
                                    Integer expectedMinYears,
                                    Integer expectedMaxYears,
                                    boolean expectedNewGraduateAllowed,
                                    EmploymentType expectedEmploymentType) {
        JobPosting jobPosting = jobPosting(title, experienceLevel);

        JobRequirement requirement = parser.parse(jobPosting);

        assertThat(requirement.minYears()).isEqualTo(expectedMinYears);
        assertThat(requirement.maxYears()).isEqualTo(expectedMaxYears);
        assertThat(requirement.newGraduateAllowed()).isEqualTo(expectedNewGraduateAllowed);
        assertThat(requirement.employmentType()).isEqualTo(expectedEmploymentType);
    }

    static Stream<Arguments> seedExperienceLevels() {
        return Stream.of(
                Arguments.of("보안 연구소 솔루션 개발자(신입)", "신입",
                        null, null, true, EmploymentType.FULL_TIME),
                Arguments.of("백엔드 개발자/디자인그룹 경력 및 신입사원 모집", "신입/경력 3년 이하",
                        null, 3, true, EmploymentType.FULL_TIME),
                Arguments.of("[OCI계열사] OCI정보통신 사내 솔루션 개발 및 기능 개선 담당", "신입/경력 2년 이하",
                        null, 2, true, EmploymentType.FULL_TIME),
                Arguments.of("Backend Engineer (Agent Platform)", "경력 5년 이상(또는 이에 준하는 실력)",
                        5, null, false, EmploymentType.FULL_TIME),
                Arguments.of("[NHN Cloud]스토리지 엔진 개발", "경력무관(신입포함)",
                        null, null, true, EmploymentType.FULL_TIME),
                Arguments.of("신라스테이 호텔분야 신입채용 (더스타팩토리프로그램)", "신입/경력",
                        null, null, true, EmploymentType.FULL_TIME),
                Arguments.of("[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴",
                        null, null, true, EmploymentType.INTERN),
                Arguments.of("Server Developer(여신)", "신입/경력",
                        null, null, true, EmploymentType.FULL_TIME)
        );
    }

    @Test
    void parse_minYearsPresent_overridesNewGraduateKeyword() {
        JobPosting jobPosting = jobPosting("백엔드 개발자", "신입/경력 5년 이상");

        JobRequirement requirement = parser.parse(jobPosting);

        assertThat(requirement.minYears()).isEqualTo(5);
        assertThat(requirement.newGraduateAllowed()).isFalse();
    }

    private JobPosting jobPosting(String title, String experienceLevel) {
        return new JobPosting(
                JobSource.SARAMIN,
                "test-external-id",
                "테스트 회사",
                title,
                "https://example.com",
                "서울",
                experienceLevel,
                "학력무관",
                "",
                "",
                CloseType.FIXED_DATE,
                LocalDateTime.now().plusDays(30)
        );
    }
}
