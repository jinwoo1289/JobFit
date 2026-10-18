package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationReasoningGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void generate_llmReturnsValidJson_parsesReasoning() {
        LlmClient llmClient = (systemPrompt, userPrompt) -> """
                {"matchedPoints": ["Spring Boot 충족 — HopeTail-BE에서 REST API 구현"], "gapPoints": ["AWS 미보유"], "checkPoints": ["재택근무 가능 여부 확인 필요"]}
                """;
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(llmClient, objectMapper, skillScoreCalculator());

        EvaluationReasoning reasoning = generator.generate(
                jobPosting(), extraction(), userProfile(), skills(), projects(), List.of(), scoreResult());

        assertThat(reasoning.matchedPoints()).containsExactly("Spring Boot 충족 — HopeTail-BE에서 REST API 구현");
        assertThat(reasoning.gapPoints()).containsExactly("AWS 미보유");
        assertThat(reasoning.checkPoints()).containsExactly("재택근무 가능 여부 확인 필요");
    }

    @Test
    void generate_llmFails_fallsBackToRuleBasedReasoning() {
        LlmClient failingLlmClient = (systemPrompt, userPrompt) -> {
            throw new RuntimeException("LLM 장애");
        };
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(failingLlmClient, objectMapper, skillScoreCalculator());

        EvaluationReasoning reasoning = generator.generate(
                jobPosting(), extraction(), userProfile(), skills(), projects(), List.of(), scoreResult());

        // Spring Boot는 보유 스킬이면서 HopeTail-BE 프로젝트 기술 스택에도 있으므로 프로젝트명이 함께 표기된다.
        assertThat(reasoning.matchedPoints()).containsExactly("Spring Boot 충족 — HopeTail-BE에서 사용");
        // AWS는 미보유 스킬이므로 gapPoints로 분류된다.
        assertThat(reasoning.gapPoints()).containsExactly("AWS 미보유");
        assertThat(reasoning.checkPoints()).isEmpty();
    }

    @Test
    void generate_llmFails_matchedSkillWithoutProject_omitsProjectName() {
        LlmClient failingLlmClient = (systemPrompt, userPrompt) -> {
            throw new RuntimeException("LLM 장애");
        };
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(failingLlmClient, objectMapper, skillScoreCalculator());
        JobRequirementExtraction jpaOnlyExtraction = new JobRequirementExtraction(
                List.of("JPA"), List.of(), null, null, true, List.of(), List.of(), JobCategory.BACKEND_DEVELOPMENT);

        EvaluationReasoning reasoning = generator.generate(
                jobPosting(), jpaOnlyExtraction, userProfile(), skills(), List.of(), List.of(), scoreResult());

        assertThat(reasoning.matchedPoints()).containsExactly("JPA 충족");
        assertThat(reasoning.gapPoints()).isEmpty();
    }

    // matchedPoints/gapPoints의 일치 여부 판정은 더 이상 LLM에 맡기지 않고, SkillScoreCalculator가 계산한
    // 결과를 프롬프트에 그대로 넘겨 LLM은 문장만 작성하도록 한다. 프롬프트에 그 결과가 실제로 전달되는지 확인한다.
    @Test
    void generate_passesSystemComputedMatchResultIntoPrompt() {
        StringBuilder capturedUserPrompt = new StringBuilder();
        LlmClient capturingLlmClient = (systemPrompt, userPrompt) -> {
            capturedUserPrompt.append(userPrompt);
            return """
                    {"matchedPoints": [], "gapPoints": [], "checkPoints": []}
                    """;
        };
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(capturingLlmClient, objectMapper, skillScoreCalculator());

        generator.generate(jobPosting(), extraction(), userProfile(), skills(), projects(), List.of(), scoreResult());

        // extraction()의 필수 기술은 Spring Boot, AWS. skills()/projects()에는 Spring Boot만 있다.
        assertThat(capturedUserPrompt.toString())
                .contains("[시스템이 판정한 필수 기술 일치 목록]\nSpring Boot")
                .contains("[시스템이 판정한 필수 기술 불일치 목록]\nAWS");
    }

    // matchedPoints는 기술 단위로 묶여, 해당 기술을 사용한 모든 프로젝트를 쉼표로 나열해 한 줄로 출력되어야 한다.
    @Test
    void generate_llmFails_skillUsedInMultipleProjects_bundlesAllProjectNamesOnOneLine() {
        LlmClient failingLlmClient = (systemPrompt, userPrompt) -> {
            throw new RuntimeException("LLM 장애");
        };
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(failingLlmClient, objectMapper, skillScoreCalculator());
        JobRequirementExtraction javaOnlyExtraction = new JobRequirementExtraction(
                List.of("Java"), List.of(), null, null, true, List.of(), List.of(), JobCategory.BACKEND_DEVELOPMENT);
        UserProfile profile = userProfile();
        List<UserSkill> skills = List.of(new UserSkill(profile, "Java"));
        List<UserProject> javaProjects = List.of(
                new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스", List.of("Java", "Spring Boot")),
                new UserProject(profile, "KakaoMCP", "MCP 연동 프로젝트", List.of("Java", "Docker")),
                new UserProject(profile, "JobFit", "적합도 평가 서비스", List.of("Java", "Spring Boot", "PostgreSQL"))
        );

        EvaluationReasoning reasoning = generator.generate(
                jobPosting(), javaOnlyExtraction, userProfile(), skills, javaProjects, List.of(), scoreResult());

        assertThat(reasoning.matchedPoints()).containsExactly("Java 충족 — HopeTail-BE, KakaoMCP, JobFit에서 사용");
    }

    private JobPosting jobPosting() {
        return new JobPosting(
                JobSource.SARAMIN, "test-external-id", "테스트 회사", "백엔드 개발자 채용",
                "https://example.com", "서울", "신입/경력", "학력무관", "Spring Boot,AWS",
                "[자격요건]\n- Spring Boot 활용 가능자\n- AWS 사용 경험",
                CloseType.FIXED_DATE, LocalDateTime.of(2026, 9, 1, 0, 0)
        );
    }

    private JobRequirementExtraction extraction() {
        return new JobRequirementExtraction(
                List.of("Spring Boot", "AWS"), List.of(), null, null, true, List.of(), List.of(),
                JobCategory.BACKEND_DEVELOPMENT
        );
    }

    private ScoreResult scoreResult() {
        return new ScoreResult(50.0, false, 0.0, false, 0.0, false, 0.0, false);
    }

    private SkillScoreCalculator skillScoreCalculator() {
        return new SkillScoreCalculator(new SkillAliasNormalizer());
    }

    private UserProfile userProfile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }

    private List<UserSkill> skills() {
        UserProfile profile = userProfile();
        return List.of(
                new UserSkill(profile, "Spring Boot"),
                new UserSkill(profile, "JPA")
        );
    }

    private List<UserProject> projects() {
        UserProfile profile = userProfile();
        return List.of(new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스", List.of("Spring Boot", "JPA")));
    }
}
