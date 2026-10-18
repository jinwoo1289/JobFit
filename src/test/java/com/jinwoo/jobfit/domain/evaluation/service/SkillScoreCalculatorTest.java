package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.SkillScore;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SkillScoreCalculatorTest {

    private final SkillScoreCalculator calculator = new SkillScoreCalculator(new SkillAliasNormalizer());

    @ParameterizedTest(name = "[{index}] {0} - skillScore={2}")
    @MethodSource("seedJobPostings")
    void calculate_seedJobPosting(String companyName, String keywords, double expectedSkillScore) {
        SkillScore actual = calculator.calculate(skills(), projects(), skillsFrom(keywords));

        assertThat(actual.value()).isCloseTo(expectedSkillScore, within(0.001));
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
                // keywords 빈 문자열 → 평가 제외(값은 0)
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

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "PostgreSQL"), new UserSkill(profile, "Git")),
                List.of(),
                skillsFrom("Python,FastAPI,PostgreSQL,SQLAlchemy,REST API,Git,Linux,React,Next.js")
        );

        // PostgreSQL, Git 두 개만 스킬로만 매칭(60점) → (60+60)/9
        assertThat(actual.value()).isCloseTo(120.0 / 9, within(0.001));
    }

    @Test
    void calculate_keywordCaseDiffersFromSkillCase_stillMatches() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "PostgreSQL"), new UserSkill(profile, "Git")),
                List.of(),
                skillsFrom("postgresql,GIT")
        );

        assertThat(actual.value()).isCloseTo(60.0, within(0.001));
    }

    @Test
    void calculate_skillOnlyNotInTechStack_scoresSixty() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "Kotlin")),
                List.of(),
                skillsFrom("Kotlin")
        );

        assertThat(actual.value()).isCloseTo(60.0, within(0.001));
    }

    @Test
    void calculate_emptyTargetSkills_returnsExcludedInsteadOfZero() {
        SkillScore actual = calculator.calculate(skills(), projects(), List.of());

        assertThat(actual.excluded()).isTrue();
        assertThat(actual.value()).isEqualTo(0.0);
    }

    // 파수에이아이(55234023) [주요 기술]: "Java, Spring Framework, RDBMS/SQL, React.js, ..."
    // "Spring Framework" 표기도 사용자가 보유한 "Spring Boot"와 동일 기술로 인식해야 한다.
    @Test
    void calculate_pasuAi_springFrameworkAliasMatchesSpringBootSkillAndTechStack() {
        SkillScore actual = calculator.calculate(skills(), projects(), List.of("Spring Framework"));

        assertThat(actual.value()).isCloseTo(100.0, within(0.001));
    }

    // 파수에이아이(55234023) [주요 기술]에 "RDBMS/SQL"이 그대로 등장한다.
    // 사용자가 MySQL/PostgreSQL을 보유하지 않으면 일치로 처리하지 않는다.
    @Test
    void calculate_pasuAi_rdbmsSqlAliasWithoutMysqlOrPostgresql_scoresZero() {
        SkillScore actual = calculator.calculate(skills(), projects(), List.of("RDBMS/SQL"));

        assertThat(actual.value()).isEqualTo(0.0);
    }

    // 원테크스탑(55205788) [DB]: "Postgresql, Oracle 등 관계형 데이터베이스" → RDBMS로 추출되는 경우를 가정.
    // 사용자가 PostgreSQL을 스킬로만 보유하면 60점(스킬만 일치)으로 처리한다.
    @Test
    void calculate_wonTechStop_rdbmsAliasMatchesPostgresqlSkillOnly_scoresSixty() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "PostgreSQL")),
                List.of(),
                List.of("RDBMS")
        );

        assertThat(actual.value()).isCloseTo(60.0, within(0.001));
    }

    // 원테크스탑(55205788)과 같이 PostgreSQL이 사용자 프로젝트 기술 스택에도 있으면 100점(완전 일치)으로 처리한다.
    @Test
    void calculate_wonTechStop_rdbmsAliasMatchesPostgresqlInProjectTechStack_scoresFull() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "PostgreSQL")),
                List.of(new UserProject(profile, "ONE-eCommerce", "이커머스 플랫폼", List.of("PostgreSQL", "Node.js"))),
                List.of("SQL")
        );

        assertThat(actual.value()).isCloseTo(100.0, within(0.001));
    }

    @Test
    void calculate_rdbmsAliasAlsoMatchesMysqlSkill() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "MySQL")),
                List.of(),
                List.of("RDBMS/SQL")
        );

        assertThat(actual.value()).isCloseTo(60.0, within(0.001));
    }

    // 한솔PNS [등록된 키워드]: "Java,Spring,React,Vue,Node.js,Python,Golang,RESTful API,RDBMS,NoSQL,VectorDB,MLOps"
    // "Spring" 단독 표기도 사용자가 보유한 "Spring Boot"와 동일 기술로 인식해야 한다.
    @Test
    void calculate_hansolPns_springAliasMatchesSpringBootSkillAndTechStack() {
        SkillScore actual = calculator.calculate(skills(), projects(), List.of("Spring"));

        assertThat(actual.value()).isCloseTo(100.0, within(0.001));
    }

    // OCI정보통신(54660418) [등록된 키워드]: "...,Generative AI,AI Coding Assistant,..."
    // 사용자가 Claude Code/Copilot/Cursor 중 하나를 보유하면 범주형 별칭으로 일치 처리해야 한다.
    @Test
    void calculate_ociJeongbotong_generativeAiAliasMatchesWhenUserHoldsClaudeCode_scoresSixty() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "Claude Code")),
                List.of(),
                List.of("Generative AI")
        );

        assertThat(actual.value()).isCloseTo(60.0, within(0.001));
    }

    // "AI Coding Assistant"도 같은 범주 별칭이며, "GitHub Copilot"처럼 제품명에 수식어가 붙어도 일치해야 한다.
    @Test
    void calculate_ociJeongbotong_aiCodingAssistantAliasMatchesWhenUserHoldsGithubCopilot_scoresFull() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "GitHub Copilot")),
                List.of(new UserProject(profile, "OCI-ERP", "ERP 기능 개선", List.of("GitHub Copilot", "Java"))),
                List.of("AI Coding Assistant")
        );

        assertThat(actual.value()).isCloseTo(100.0, within(0.001));
    }

    // "AI 코딩 도구"(한글 표기)도 같은 범주 별칭으로 인식해야 하며, Cursor 보유로도 일치해야 한다.
    @Test
    void calculate_koreanAiCodingToolAliasMatchesWhenUserHoldsCursor_scoresSixty() {
        UserProfile profile = profile();

        SkillScore actual = calculator.calculate(
                List.of(new UserSkill(profile, "Cursor")),
                List.of(),
                List.of("AI 코딩 도구")
        );

        assertThat(actual.value()).isCloseTo(60.0, within(0.001));
    }

    // 사용자가 Claude Code/Copilot/Cursor 중 아무것도 보유하지 않으면 범주형 별칭이라도 일치하지 않는다.
    @Test
    void calculate_aiCodingToolAlias_withoutAnyEquivalentSkill_scoresZero() {
        SkillScore actual = calculator.calculate(skills(), projects(), List.of("AI Coding Assistant"));

        assertThat(actual.value()).isEqualTo(0.0);
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

    private static List<String> skillsFrom(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .toList();
    }
}
