package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ConditionCheckResult;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
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

import java.util.Set;
import java.util.List;

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

        ConditionCheckResult result = checker.check(userProfile, jobPosting, defaultExtraction());

        assertThat(result.passed()).isEqualTo(expectedPassed);
    }

    @Test
    void check_linkAlpha_failsOnCareer() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "Backend Engineer (Agent Platform)", "경력 5년 이상(또는 이에 준하는 실력)", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(userProfile, jobPosting, defaultExtraction());

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).contains("경력 5년 이상 요구, 신입 지원 불가");
    }

    // 셀트리온처럼 experienceLevel이 "경력"뿐이어도 본문 추출 결과에 연수가 있으면 그 연수로 사유를 표시한다.
    @Test
    void check_careerOnlyWithExtractedMinYears_failsWithYearsReason() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting("서버/Cloud 엔지니어", "경력", "2026-08-15T23:59:59");
        JobRequirementExtraction extraction = extractionOf(5, null, JobCategory.DEVOPS);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).containsExactly("경력 5년 이상 요구, 신입 지원 불가");
    }

    @Test
    void check_careerOnlyWithoutMinYears_failsWithCareerOnlyReason() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting("서버/Cloud 엔지니어", "경력", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(userProfile, jobPosting, defaultExtraction());

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).containsExactly("경력직 채용 공고, 신입 지원 불가");
        assertThat(result.failedReasons().getFirst().split("지원 불가", -1)).hasSize(2);
    }

    @Test
    void check_newGraduateAllowed_hasNoCareerReason() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting("Server Developer(여신)", "신입/경력", "2026-08-15T23:59:59");
        JobRequirementExtraction extraction = extractionOf(3, null, JobCategory.BACKEND_DEVELOPMENT);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isTrue();
        assertThat(result.failedReasons()).noneMatch(reason -> reason.contains("경력"));
    }

    @Test
    void check_nubilab_failsOnEmploymentType() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(userProfile, jobPosting, defaultExtraction());

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons())
                .contains("희망 고용형태(FULL_TIME)와 공고 고용형태(INTERN) 불일치");
    }

    // 복수 선택: 공고 고용형태(누비랩=INTERN)가 선택한 집합에 포함되면 통과한다.
    @Test
    void check_multipleEmploymentTypesIncludingPostingType_passes() {
        UserProfile userProfile = newGraduateUserWith(Set.of(EmploymentType.FULL_TIME, EmploymentType.INTERN));
        JobPosting internPosting = jobPosting(
                "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴", "2026-08-15T23:59:59");
        JobPosting fullTimePosting = jobPosting("Server Developer(여신)", "신입/경력", "2026-08-15T23:59:59");

        assertThat(checker.check(userProfile, internPosting, defaultExtraction()).passed()).isTrue();
        assertThat(checker.check(userProfile, fullTimePosting, defaultExtraction()).passed()).isTrue();
    }

    @Test
    void check_multipleEmploymentTypesExcludingPostingType_failsWithSortedReason() {
        UserProfile userProfile = newGraduateUserWith(Set.of(EmploymentType.FREELANCE, EmploymentType.CONTRACT));
        JobPosting internPosting = jobPosting(
                "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(userProfile, internPosting, defaultExtraction());

        assertThat(result.passed()).isFalse();
        // 사유 문구는 Set 순회 순서와 무관하게 enum 선언 순서로 고정된다.
        assertThat(result.failedReasons())
                .containsExactly("희망 고용형태(CONTRACT, FREELANCE)와 공고 고용형태(INTERN) 불일치");
    }

    // 아무것도 선택하지 않으면 무관(기존 ANY)으로 보고 어떤 고용형태 공고든 통과한다.
    @Test
    void check_noEmploymentTypeSelected_passesAnyPostingType() {
        UserProfile userProfile = newGraduateUserWith(Set.of());
        JobPosting internPosting = jobPosting(
                "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴", "2026-08-15T23:59:59");
        JobPosting fullTimePosting = jobPosting("Server Developer(여신)", "신입/경력", "2026-08-15T23:59:59");

        assertThat(checker.check(userProfile, internPosting, defaultExtraction()).failedReasons()).isEmpty();
        assertThat(checker.check(userProfile, fullTimePosting, defaultExtraction()).failedReasons()).isEmpty();
    }

    @Test
    void check_yearsOfExperienceExceedsMaxYears_failsOnMaxYears() {
        UserProfile userProfile = experiencedFullTimeUser(4);
        JobPosting jobPosting = jobPosting(
                "백엔드 개발자/디자인그룹 경력 및 신입사원 모집", "신입/경력 3년 이하", "2026-08-15T23:59:59");
        JobRequirementExtraction extraction = extractionOf(null, 3, JobCategory.BACKEND_DEVELOPMENT);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).contains("경력 3년 이하 요구, 지원자 경력 4년 초과");
    }

    @Test
    void check_jobCategoryOther_failsOnJobCategory() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "보안 연구소 솔루션 개발자(신입)", "신입", "2026-08-26T23:59:59");
        JobRequirementExtraction extraction = extractionOf(null, null, JobCategory.OTHER);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons())
                .contains("공고 직무 카테고리(OTHER)가 희망 직무와 무관합니다.");
    }

    @Test
    void check_jobCategoryNull_passesAsUndetermined() {
        // 정규식 fallback은 jobCategory를 항상 null로 반환하므로, null은 "판정 불가"로 보고 통과시켜야 함
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "보안 연구소 솔루션 개발자(신입)", "신입", "2026-08-26T23:59:59");
        JobRequirementExtraction extraction = extractionOf(null, null, null);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isTrue();
        assertThat(result.notices()).isEmpty();
    }

    // IT 통합 채용은 개발 계열 희망자에게 통과시키되, 지원 불가 사유가 아닌 별도 안내로 직무 범위 확인을 알린다.
    @Test
    void check_jobCategoryItGeneralWithDevelopmentDesiredJob_passesWithNotice() {
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "2026년 2차 정규직 신입사원 채용 공고 (IT)", "신입", "2026-08-26T23:59:59");
        JobRequirementExtraction extraction = extractionOf(null, null, JobCategory.IT_GENERAL);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isTrue();
        assertThat(result.failedReasons()).isEmpty();
        assertThat(result.notices()).containsExactly(RequiredConditionChecker.IT_GENERAL_NOTICE);
    }

    @Test
    void check_jobCategoryItGeneralWithNonDevelopmentDesiredJob_fails() {
        UserProfile userProfile = new UserProfile("마케팅 기획자", CareerLevel.NEW_GRADUATE, null, "서울", Set.of(EmploymentType.FULL_TIME));
        JobPosting jobPosting = jobPosting(
                "2026년 2차 정규직 신입사원 채용 공고 (IT)", "신입", "2026-08-26T23:59:59");
        JobRequirementExtraction extraction = extractionOf(null, null, JobCategory.IT_GENERAL);

        ConditionCheckResult result = checker.check(userProfile, jobPosting, extraction);

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).containsExactly("IT 통합 채용 공고로, 희망 직무(마케팅 기획자)가 개발 계열이 아닙니다.");
        assertThat(result.notices()).isEmpty();
    }

    // 회귀 테스트: application-dev.properties에 2026-08-06로 고정된 clock이 방치되어, 실제로는
    // 지난 마감일(킨코스코리아, 2026-08-07 마감)이 "아직 안 지남"으로 오판정되어 FIT 1위로 노출된 적이 있다.
    // "오늘"을 기준으로 한 clock이 주입되면 마감 지난 공고는 반드시 마감 사유로 UNFIT 처리되어야 한다.
    @Test
    void check_kincos_pastDeadlineAsOfToday_failsOnDeadlineWithReason() {
        Clock today = Clock.fixed(
                LocalDateTime.of(2026, 10, 18, 0, 0, 0).atZone(ZoneId.systemDefault()).toInstant(),
                ZoneId.systemDefault()
        );
        RequiredConditionChecker todayChecker = new RequiredConditionChecker(new JobRequirementParser(), today);
        UserProfile userProfile = newGraduateFullTimeUser();
        JobPosting jobPosting = jobPosting(
                "백엔드 개발자/디자인그룹 경력 및 신입사원 모집", "신입/경력 3년 이하", "2026-08-07T23:59:59");

        ConditionCheckResult result = todayChecker.check(userProfile, jobPosting, defaultExtraction());

        assertThat(result.passed()).isFalse();
        assertThat(result.failedReasons()).contains("마감일이 지난 공고입니다.");
        assertThat(result.expired()).isTrue();
    }

    // 마감 외 사유(경력 요구)로 UNFIT인 공고는 expired=false여야 화면이 마감 공고 영역으로 잘못 분리하지 않는다.
    @Test
    void check_failsForNonDeadlineReason_isNotExpired() {
        JobPosting jobPosting = jobPosting(
                "Backend Engineer (Agent Platform)", "경력 5년 이상(또는 이에 준하는 실력)", "2026-08-15T23:59:59");

        ConditionCheckResult result = checker.check(newGraduateFullTimeUser(), jobPosting, defaultExtraction());

        assertThat(result.passed()).isFalse();
        assertThat(result.expired()).isFalse();
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
                Set.of(EmploymentType.FULL_TIME)
        );
    }

    private static UserProfile newGraduateUserWith(Set<EmploymentType> employmentTypes) {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", employmentTypes);
    }

    private static UserProfile experiencedFullTimeUser(int yearsOfExperience) {
        return new UserProfile(
                "백엔드 개발자",
                CareerLevel.EXPERIENCED,
                yearsOfExperience,
                "서울",
                Set.of(EmploymentType.FULL_TIME)
        );
    }

    private static JobRequirementExtraction defaultExtraction() {
        return extractionOf(null, null, JobCategory.BACKEND_DEVELOPMENT);
    }

    private static JobRequirementExtraction extractionOf(Integer minYears, Integer maxYears, JobCategory jobCategory) {
        return new JobRequirementExtraction(
                List.of(), List.of(), minYears, maxYears, true, List.of(), List.of(), jobCategory
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
