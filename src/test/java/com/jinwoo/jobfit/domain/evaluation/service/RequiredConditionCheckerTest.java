package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ConditionCheckResult;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class RequiredConditionCheckerTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 8, 6, 0, 0, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
    );

    private final RequiredConditionChecker checker = new RequiredConditionChecker(new JobRequirementParser(), FIXED_CLOCK);

    @ParameterizedTest(name = "[{index}] {0} - passed={2}")
    @MethodSource("seedJobPostings")
    void check_seedJobPosting(String companyName, JobPosting jobPosting, boolean expectedPassed) {
        UserProfile userProfile = newGraduateFullTimeUser();

        ConditionCheckResult result = checker.check(userProfile, jobPosting);

        assertThat(result.passed()).isEqualTo(expectedPassed);
    }

    @Test
    void check_linkAlpha_failsOnCareer() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "Backend Engineer (Agent Platform)", "경력 5년 이상(또는 이에 준하는 실력)", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(userProfile, jobPosting);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).contains("경력 5년 이상 요구, 신입 지원 불가");
    }

    @Test
    void check_nubilab_failsOnEmploymentType() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(userProfile, jobPosting);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons())
                .contains("희망 고용형태(FULL_TIME)와 공고 고용형태(INTERN) 불일치");
    }

    static Stream<Arguments> seedJobPostings() {
        return Stream.of(
                Arguments.of("위드네트웍스", jobPosting(
                        "보안 연구소 솔루션 개발자(신입)", "신입", "2026-08-26T23:59:59"), true),
                Arguments.of("킨코스코리아", jobPosting(
                        "백엔드 개발자/디자인그룹 경력 및 신입사원 모집", "신입/경력 3년 이하", "2026-08-07T23:59:59"), true),
                Arguments.of("OCI정보통신", jobPosting(
                        "[OCI계열사] OCI정보통신 사내 솔루션 개발 및 기능 개선 담당", "신입/경력 2년 이하", "2026-09-04T23:59:59"), true),
                Arguments.of("링크알파코리아", jobPosting(
                        "Backend Engineer (Agent Platform)", "경력 5년 이상(또는 이에 준하는 실력)", "2026-08-15T23:59:59"), false),
                Arguments.of("엔에이치엔클라우드", jobPosting(
                        "[NHN Cloud]스토리지 엔진 개발", "경력무관(신입포함)", "2026-08-15T23:59:59"), true),
                Arguments.of("신라에이치엠", jobPosting(
                        "신라스테이 호텔분야 신입채용 (더스타팩토리프로그램)", "신입/경력", "2026-08-10T23:59:59"), true),
                Arguments.of("누비랩", jobPosting(
                        "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴", "2026-08-15T23:59:59"), false),
                Arguments.of("토스뱅크", jobPosting(
                        "Server Developer(여신)", "신입/경력", "2026-08-15T23:59:59"), true)
        );
    }

    private static UserProfile newGraduateFullTimeUser() {
        return new UserProfile(
                "백엔드 개발자",
                CareerLevel.NEW_GRADUATE,
                null,
                "서울",
                EmploymentType.FULL_TIME
        );
    }

    private static JobPosting jobPosting(String title, String experienceLevel, String expirationAt) {
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
                LocalDateTime.parse(expirationAt)
        );
    }
}
