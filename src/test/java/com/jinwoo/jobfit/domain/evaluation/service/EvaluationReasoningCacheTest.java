package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.entity.EvaluationReasoningCacheEntity;
import com.jinwoo.jobfit.domain.evaluation.repository.EvaluationReasoningCacheRepository;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * checkPoints 캐시가 키(공고·프로필·프로필 해시·추출 해시·근거 프롬프트 버전) 기준으로 재사용·무효화되는지 검증한다.
 * LLM은 호출 횟수를 세는 람다로, 저장소는 저장한 행 하나를 돌려주는 mock으로 대체해 실제 호출이 없다.
 */
class EvaluationReasoningCacheTest {

    private static final String LLM_RESPONSE = """
            {"matchedPoints": [], "gapPoints": [], "checkPoints": ["근무지, 재택 여부 확인 필요"]}
            """;

    private final AtomicInteger llmCalls = new AtomicInteger();
    private final AtomicReference<EvaluationReasoningCacheEntity> storedRow = new AtomicReference<>();
    private final EvaluationReasoningCacheRepository repository = inMemoryRepository();
    private final EvaluationReasoningGenerator generator = generatorWith((systemPrompt, userPrompt) -> {
        llmCalls.incrementAndGet();
        return LLM_RESPONSE;
    });

    // 가중치는 근거 생성 입력이 아니므로, 같은 공고·프로필·추출로 다시 평가하면 LLM을 부르지 않는다.
    @Test
    void generate_sameInputsTwice_secondCallUsesCacheWithoutLlm() {
        generate(extraction(List.of("Java")), skills("Java"));
        EvaluationReasoning second = generate(extraction(List.of("Java")), skills("Java"));

        assertThat(llmCalls.get()).isEqualTo(1);
        assertThat(second.checkPoints()).containsExactly("근무지, 재택 여부 확인 필요");
        // 쉼표가 들어간 문장도 JSON 배열로 저장돼 한 항목으로 복원되어야 한다.
        assertThat(second.checkPoints()).hasSize(1);
    }

    // 캐시를 써도 matchedPoints/gapPoints는 매번 규칙 기반으로 다시 계산한다.
    @Test
    void generate_cacheHit_recomputesRuleBasedPoints() {
        generate(extraction(List.of("Java", "AWS")), skills("Java"));
        EvaluationReasoning second = generate(extraction(List.of("Java", "AWS")), skills("Java"));

        assertThat(second.matchedPoints()).containsExactly("Java 충족");
        assertThat(second.gapPoints()).containsExactly("AWS 미보유");
    }

    @Test
    void generate_profileChanged_regeneratesAndOverwritesSameRow() {
        generate(extraction(List.of("Java")), skills("Java"));
        EvaluationReasoningCacheEntity firstRow = storedRow.get();

        generate(extraction(List.of("Java")), skills("Java", "Kotlin"));

        assertThat(llmCalls.get()).isEqualTo(2);
        assertThat(storedRow.get()).isSameAs(firstRow);
    }

    // 프롬프트 버전 변경이든 강제 재추출이든, 추출 결과 내용이 바뀌면 근거도 다시 만든다.
    @Test
    void generate_extractionChanged_regenerates() {
        generate(extraction(List.of("Java")), skills("Java"));
        generate(extraction(List.of("Java", "AWS")), skills("Java"));

        assertThat(llmCalls.get()).isEqualTo(2);
    }

    @Test
    void generate_reasoningPromptVersionChanged_regenerates() {
        generate(extraction(List.of("Java")), skills("Java"));
        ReflectionTestUtils.setField(storedRow.get(), "reasoningPromptVersion",
                EvaluationReasoningGenerator.REASONING_PROMPT_VERSION - 1);

        generate(extraction(List.of("Java")), skills("Java"));

        assertThat(llmCalls.get()).isEqualTo(2);
    }

    // 실패 결과는 캐시하지 않아, LLM이 회복되면 다음 평가에서 다시 생성된다.
    @Test
    void generate_llmFails_returnsRuleBasedWithoutCaching() {
        EvaluationReasoningGenerator failingGenerator = generatorWith((systemPrompt, userPrompt) -> {
            llmCalls.incrementAndGet();
            throw new IllegalStateException("429 Too Many Requests: insufficient_quota");
        });

        EvaluationReasoning reasoning = failingGenerator.generate(jobPosting(), extraction(List.of("Java")), userProfile(),
                skills("Java"), List.of(), List.<UserCertificate>of(), scoreResult());

        assertThat(reasoning.checkPoints()).isEmpty();
        assertThat(reasoning.matchedPoints()).containsExactly("Java 충족");
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    // 같은 요청에서 LLM이 연속 실패해 breaker가 열려 있으면 근거 생성 LLM도 부르지 않는다.
    @Test
    void generate_breakerOpen_skipsLlm() {
        LlmCallBreaker breaker = new LlmCallBreaker();
        for (int i = 0; i < LlmCallBreaker.CONSECUTIVE_FAILURE_THRESHOLD; i++) {
            breaker.recordFailure();
        }

        EvaluationReasoning reasoning = generator.generate(jobPosting(), extraction(List.of("Java")), userProfile(),
                skills("Java"), List.of(), List.of(), scoreResult(), breaker);

        assertThat(llmCalls.get()).isZero();
        assertThat(reasoning.checkPoints()).isEmpty();
        assertThat(reasoning.matchedPoints()).containsExactly("Java 충족");
    }

    private EvaluationReasoning generate(JobRequirementExtraction extraction, List<UserSkill> skills) {
        return generator.generate(jobPosting(), extraction, userProfile(), skills, List.of(), List.of(), scoreResult());
    }

    private EvaluationReasoningGenerator generatorWith(LlmClient llmClient) {
        SkillAliasNormalizer normalizer = new SkillAliasNormalizer();
        return new EvaluationReasoningGenerator(llmClient, new ObjectMapper(), new SkillScoreCalculator(normalizer),
                normalizer, repository);
    }

    private EvaluationReasoningCacheRepository inMemoryRepository() {
        EvaluationReasoningCacheRepository mock = Mockito.mock(EvaluationReasoningCacheRepository.class);
        Mockito.when(mock.findByJobPostingIdAndUserProfileId(Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> Optional.ofNullable(storedRow.get()));
        Mockito.when(mock.save(Mockito.any(EvaluationReasoningCacheEntity.class)))
                .thenAnswer(invocation -> {
                    storedRow.set(invocation.getArgument(0));
                    return invocation.getArgument(0);
                });
        return mock;
    }

    private JobPosting jobPosting() {
        JobPosting jobPosting = new JobPosting(
                JobSource.SARAMIN, "test-external-id", "테스트 회사", "백엔드 개발자 채용",
                "https://example.com", "서울", "신입", "학력무관", "Java,AWS",
                "[자격요건]\n- Java 개발 경험\n- AWS 사용 경험",
                CloseType.FIXED_DATE, LocalDateTime.of(2026, 9, 1, 0, 0)
        );
        ReflectionTestUtils.setField(jobPosting, "id", 1L);
        return jobPosting;
    }

    private UserProfile userProfile() {
        UserProfile userProfile = new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
        ReflectionTestUtils.setField(userProfile, "id", 11L);
        return userProfile;
    }

    private List<UserSkill> skills(String... names) {
        UserProfile profile = userProfile();
        return Arrays.stream(names).map(name -> new UserSkill(profile, name)).toList();
    }

    private JobRequirementExtraction extraction(List<String> requiredSkills) {
        return new JobRequirementExtraction(requiredSkills, List.of(), null, null, true, List.of(), List.of(),
                JobCategory.BACKEND_DEVELOPMENT);
    }

    private ScoreResult scoreResult() {
        return new ScoreResult(100.0, false, 0.0, false, 0.0, false, 0.0, false);
    }
}
