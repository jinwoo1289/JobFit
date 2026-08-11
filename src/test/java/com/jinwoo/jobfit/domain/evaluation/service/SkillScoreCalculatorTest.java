package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SkillScoreCalculatorTest {

    private final SkillScoreCalculator calculator = new SkillScoreCalculator();

    @ParameterizedTest(name = "[{index}] {0} - skillScore={2}")
    @MethodSource("seedJobPostings")
    void calculate_seedJobPosting(String companyName, String keywords, double expectedSkillScore) {
        JobPosting jobPosting = jobPosting(keywords, "설명");

        double actual = calculator.calculate(skills(), projects(), jobPosting);

        assertThat(actual).isCloseTo(expectedSkillScore, within(0.001));
    }

    static Stream<Arguments> seedJobPostings() {
        return Stream.of(
                // Docker, Redis만 보유 스킬+프로젝트 스택 모두 일치 → (100+100)/8
                Arguments.of("위드네트웍스", "Python,Django,Docker,Linux,PostgreSQL,Redis,FastAPI,SQL", 25.0),
                // 겹치는 스킬 없음
                Arguments.of("킨코스코리아", "Python,FastAPI,PostgreSQL,SQLAlchemy,REST API,Git,Linux,React,Next.js", 0.0),
                // Java, Spring Boot 모두 일치 → (100+100)/8
                Arguments.of("OCI정보통신", "React,Java,Spring Boot,Generative AI,AI Coding Assistant,Jira,Bitbucket,Confluence", 25.0),
                // AWS만 일치 → 100/13
                Arguments.of("링크알파코리아", "Python,TypeScript,LLM,AI Agent,MCP,Claude Code,Cursor,PostgreSQL,AWS,Azure,GCP,React,Next.js", 100.0 / 13),
                // 겹치는 스킬 없음
                Arguments.of("엔에이치엔클라우드", "C,C++,Go,Python", 0.0),
                // keywords 빈 문자열 → 0점
                Arguments.of("신라에이치엠", "", 0.0),
                // Kotlin(스킬만,60) + Spring Boot/JPA/Redis/AWS(스킬+프로젝트,100) → (60+100*4)/20
                Arguments.of("누비랩", "Kotlin,Spring Boot,Spring Batch,QueryDSL,JPA,Node.js,JavaScript(ES6+),Nest.js,TypeORM,Python,PostgreSQL,MySQL,Redis,Apache Kafka,Airflow,AWS,Github,Slack,Jira,Notion", 460.0 / 20),
                // Java/Spring Boot(스킬+프로젝트,100) + Kotlin(스킬만,60) → (100+60+100)/6
                Arguments.of("토스뱅크", "Java,Kotlin,Spring Boot,MSA,코어뱅킹,MDD", 260.0 / 6)
        );
    }

    @Test
    void calculate_kincosWithPostgresAndGitSkills_matchesProportionally() {
        UserProfile profile = profile();
        JobPosting jobPosting = jobPosting(
                "Python,FastAPI,PostgreSQL,SQLAlchemy,REST API,Git,Linux,React,Next.js", "설명");

        double actual = calculator.calculate(
                List.of(new UserSkill(profile, "PostgreSQL"), new UserSkill(profile, "Git")),
                List.of(),
                jobPosting
        );

        // PostgreSQL, Git 두 개만 스킬로만 매칭(60점) → (60+60)/9
        assertThat(actual).isCloseTo(120.0 / 9, within(0.001));
    }

    @Test
    void calculate_keywordCaseDiffersFromSkillCase_stillMatches() {
        UserProfile profile = profile();
        JobPosting jobPosting = jobPosting("postgresql,GIT", "설명");

        double actual = calculator.calculate(
                List.of(new UserSkill(profile, "PostgreSQL"), new UserSkill(profile, "Git")),
                List.of(),
                jobPosting
        );

        assertThat(actual).isCloseTo(60.0, within(0.001));
    }

    @Test
    void calculate_skillOnlyNotInTechStack_scoresSixty() {
        UserProfile profile = profile();
        JobPosting jobPosting = jobPosting("Kotlin", "설명");

        double actual = calculator.calculate(
                List.of(new UserSkill(profile, "Kotlin")),
                List.of(),
                jobPosting
        );

        assertThat(actual).isCloseTo(60.0, within(0.001));
    }

    private static List<UserSkill> skills() {
        UserProfile profile = profile();
        return List.of(
                new UserSkill(profile, "Java"),
                new UserSkill(profile, "Spring Boot"),
                new UserSkill(profile, "Kotlin"),
                new UserSkill(profile, "JPA"),
                new UserSkill(profile, "Redis"),
                new UserSkill(profile, "AWS"),
                new UserSkill(profile, "Docker")
        );
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

    private static JobPosting jobPosting(String keywords, String description) {
        return new JobPosting(
                JobSource.SARAMIN,
                "test-external-id",
                "테스트 회사",
                "테스트 공고",
                "https://example.com",
                "서울",
                "신입",
                "학력무관",
                keywords,
                description,
                CloseType.FIXED_DATE,
                LocalDateTime.now().plusDays(10)
        );
    }
}
