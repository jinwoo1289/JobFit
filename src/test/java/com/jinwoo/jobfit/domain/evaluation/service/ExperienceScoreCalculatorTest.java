package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ExperienceScoreCalculatorTest {

    private final ExperienceScoreCalculator calculator = new ExperienceScoreCalculator();

    // 프로젝트별 (techStack ∩ requiredSkills) / requiredSkills 크기 중 최댓값 = experienceScore
    @ParameterizedTest(name = "[{index}] {0} - experienceScore={2}")
    @MethodSource("seedJobPostings")
    void calculate_seedJobPosting(String companyName, List<String> requiredSkills, double expectedExperienceScore) {
        double actual = calculator.calculate(projects(), requiredSkills);

        assertThat(actual).isCloseTo(expectedExperienceScore, within(0.001));
    }

    static Stream<Arguments> seedJobPostings() {
        return Stream.of(
                // HopeTail-BE(java, spring boot, jpa, redis, aws) 중 redis 매칭, KakaoMCP 중 docker 매칭 → 1/8
                Arguments.of("위드네트웍스",
                        List.of("Python", "Django", "Docker", "Linux", "PostgreSQL", "Redis", "FastAPI", "SQL"),
                        100.0 / 8),
                // 두 프로젝트 모두 매칭 없음 → 0
                Arguments.of("킨코스코리아",
                        List.of("Python", "FastAPI", "PostgreSQL", "SQLAlchemy", "REST API", "Git", "Linux", "React", "Next.js"),
                        0.0),
                // 두 프로젝트 모두 java, spring boot 매칭 → 2/8
                Arguments.of("OCI정보통신",
                        List.of("React", "Java", "Spring Boot", "Generative AI", "AI Coding Assistant", "Jira", "Bitbucket", "Confluence"),
                        200.0 / 8),
                // HopeTail-BE에서 aws만 매칭 → 1/13
                Arguments.of("링크알파코리아",
                        List.of("Python", "TypeScript", "LLM", "AI Agent", "MCP", "Claude Code", "Cursor", "PostgreSQL", "AWS", "Azure", "GCP", "React", "Next.js"),
                        100.0 / 13),
                // 매칭 없음 → 0
                Arguments.of("엔에이치엔클라우드",
                        List.of("C", "C++", "Go", "Python"),
                        0.0),
                // requiredSkills 없음 → 0
                Arguments.of("신라에이치엠",
                        List.<String>of(),
                        0.0),
                // HopeTail-BE에서 spring boot, jpa, redis, aws 매칭 → 4/20
                Arguments.of("누비랩",
                        List.of("Kotlin", "Spring Boot", "Spring Batch", "QueryDSL", "JPA", "Node.js", "JavaScript(ES6+)", "Nest.js", "TypeORM",
                                "Python", "PostgreSQL", "MySQL", "Redis", "Apache Kafka", "Airflow", "AWS", "Github", "Slack", "Jira", "Notion"),
                        20.0),
                // 두 프로젝트 모두 java, spring boot 매칭 → 2/6
                Arguments.of("토스뱅크",
                        List.of("Java", "Kotlin", "Spring Boot", "MSA", "코어뱅킹", "MDD"),
                        200.0 / 6)
        );
    }

    @Test
    void calculate_requiredSkillsEmpty_returnsZero() {
        double actual = calculator.calculate(projects(), List.of());

        assertThat(actual).isEqualTo(0.0);
    }

    @Test
    void calculate_userHasNoProjects_returnsZero() {
        double actual = calculator.calculate(List.of(), List.of("Java", "Spring Boot"));

        assertThat(actual).isEqualTo(0.0);
    }

    @Test
    void calculate_usesMaxRatioAcrossProjects_caseInsensitive() {
        UserProfile profile = profile();
        List<UserProject> projects = List.of(
                new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스",
                        List.of("Java", "Spring Boot", "JPA", "MySQL", "Redis", "Docker", "AWS", "REST API", "Git")),
                new UserProject(profile, "JobFit", "적합도 평가 서비스",
                        List.of("Java", "Spring Boot", "JPA", "PostgreSQL", "Docker", "JUnit", "REST API", "Git"))
        );

        double tossBankScore = calculator.calculate(projects, List.of("java", "kotlin", "spring boot"));
        double kincosScore = calculator.calculate(projects, List.of("python", "fastapi", "postgresql", "rest api", "git"));

        assertThat(tossBankScore).isCloseTo(200.0 / 3, within(0.001));
        assertThat(kincosScore).isCloseTo(60.0, within(0.001));
    }

    private static List<UserProject> projects() {
        UserProfile profile = profile();
        return List.of(
                new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스", List.of("Java", "Spring Boot", "JPA", "Redis", "AWS")),
                new UserProject(profile, "KakaoMCP", "MCP 연동 프로젝트", List.of("Java", "Spring Boot", "Docker"))
        );
    }

    private static UserProfile profile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }
}
