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
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(llmClient, objectMapper);

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
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(failingLlmClient, objectMapper);

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
        EvaluationReasoningGenerator generator = new EvaluationReasoningGenerator(failingLlmClient, objectMapper);
        JobRequirementExtraction jpaOnlyExtraction = new JobRequirementExtraction(
                List.of("JPA"), List.of(), null, null, true, List.of(), JobCategory.BACKEND_DEVELOPMENT);

        EvaluationReasoning reasoning = generator.generate(
                jobPosting(), jpaOnlyExtraction, userProfile(), skills(), List.of(), List.of(), scoreResult());

        assertThat(reasoning.matchedPoints()).containsExactly("JPA 충족");
        assertThat(reasoning.gapPoints()).isEmpty();
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
                List.of("Spring Boot", "AWS"), List.of(), null, null, true, List.of(),
                JobCategory.BACKEND_DEVELOPMENT
        );
    }

    private ScoreResult scoreResult() {
        return new ScoreResult(50.0, 0.0, 0.0, 0.0);
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
