package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.JsonSchemaFormat;
import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.entity.JobRequirementExtractionEntity;
import com.jinwoo.jobfit.domain.evaluation.exception.LlmResponseException;
import com.jinwoo.jobfit.domain.evaluation.repository.JobRequirementExtractionRepository;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementReExtraction;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JobRequirementExtractor의 개념어 후처리 필터를 LLM 호출 없이(FakeLlmClient 역할의 람다) 검증한다.
 * 파수에이아이(55234023), 원테크스탑(55205788) 공고의 실제 [주요 기술]/키워드를 기준으로 한다.
 */
class JobRequirementExtractorTest {

    private final JobRequirementParser jobRequirementParser = new JobRequirementParser();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 파수에이아이(55234023) [주요 기술]: "Java, Spring Framework, RDBMS/SQL, React.js, Redux, TypeScript, HTTP,
    // 웹 보안 (SSL, XSS, CORS), 비동기 프로그래밍, 데이터 시각화, 동시성 프로그래밍, 네트워크 통신 프로그래밍"
    // 처럼 구체적 기술명과 개념어가 한 섹션에 섞여 있을 때, 개념어만 제거되어야 한다.
    @Test
    void extract_pasuAiPosting_removesConceptSkillsFromRequiredSkills() {
        JobPosting jobPosting = pasuAiJobPosting();
        String llmResponse = """
                {"requiredSkills": ["Java","Spring Framework","RDBMS/SQL","React.js","Redux","TypeScript","HTTP","웹 보안","비동기 프로그래밍","데이터 시각화","동시성 프로그래밍","네트워크 통신 프로그래밍"], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.requiredSkills()).containsExactly(
                "Java", "Spring Framework", "RDBMS/SQL", "React.js", "Redux", "TypeScript"
        );
    }

    @Test
    void extract_pasuAiPosting_removesConceptSkillsFromPreferredSkillsToo() {
        JobPosting jobPosting = pasuAiJobPosting();
        String llmResponse = """
                {"requiredSkills": ["Java"], "preferredSkills": ["동시성 프로그래밍","데이터 시각화","Redux"], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.preferredSkills()).containsExactly("Redux");
    }

    // 파수에이아이(55234023) [주요 기술]의 "HTTP, 웹 보안 (SSL, XSS, CORS)"에서 괄호 안 보안 개념까지 기술로 뽑히는 경우.
    // LLM이 표기를 바꿔 내보내도 걸러지도록 대소문자와 무관하게 제거되어야 한다.
    @Test
    void extract_pasuAiPosting_removesWebProtocolAndSecurityConceptsIgnoringCase() {
        JobPosting jobPosting = pasuAiJobPosting();
        String llmResponse = """
                {"requiredSkills": ["Java","HTTP","SSL","XSS","CORS","React.js"], "preferredSkills": ["Http","ssl","Xss","cors","Redux"], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "FULLSTACK_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.requiredSkills()).containsExactly("Java", "React.js");
        assertThat(extraction.preferredSkills()).containsExactly("Redux");
    }

    // 블록리스트는 정확히 일치하는 경우만 제거하므로, 보안 개념명이 포함된 구체적인 기술명은 남겨야 한다.
    @Test
    void extract_keepsSkillsThatOnlyContainBlockedTerms() {
        JobPosting jobPosting = pasuAiJobPosting();
        String llmResponse = """
                {"requiredSkills": ["HTTP/2","OpenSSL","Spring Security"], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.requiredSkills()).containsExactly("HTTP/2", "OpenSSL", "Spring Security");
    }

    // 원테크스탑(55205788)은 핵심 기술 스택만 나열되어 개념어가 섞이지 않는 경우로, 필터가 멀쩡한 기술명을
    // 지우지 않는지 확인한다.
    @Test
    void extract_wonTechStopPosting_keepsAllSkillsWhenNoConceptWordsPresent() {
        JobPosting jobPosting = wonTechStopJobPosting();
        String llmResponse = """
                {"requiredSkills": [], "preferredSkills": ["Java","Python","Node.js","NestJS","Next.js","React","JSP","PostgreSQL","Oracle","GCP","Git","CI/CD"], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "FULLSTACK_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.requiredSkills()).isEmpty();
        assertThat(extraction.preferredSkills()).containsExactly(
                "Java", "Python", "Node.js", "NestJS", "Next.js", "React", "JSP", "PostgreSQL", "Oracle", "GCP", "Git", "CI/CD"
        );
    }

    // 링크알파코리아(54428738) [등록된 키워드]: "Python,TypeScript,LLM,AI Agent,MCP,..."
    // 분야명 "LLM"/"AI"는 제거하되, "AI Agent"처럼 구체적인 제품/개념을 가리키는 복합어는 그대로 둔다.
    @Test
    void extract_linkAlphaPosting_removesGenericLlmAndAiFieldNamesButKeepsCompoundTerms() {
        JobPosting jobPosting = linkAlphaJobPosting();
        String llmResponse = """
                {"requiredSkills": ["Python","TypeScript","LLM","AI","AI Agent","생성형 AI","MCP","PostgreSQL","AWS"], "preferredSkills": [], "minYears": 5, "maxYears": null, "newGraduateAllowed": false, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "AI_ML"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.requiredSkills()).containsExactly(
                "Python", "TypeScript", "AI Agent", "MCP", "PostgreSQL", "AWS"
        );
    }

    // 블록리스트가 확장되기 전에 캐시된 결과(예: "AI", "웹 보안" 등 개념어가 그대로 저장된 데이터)도
    // 조회 시점에 동일한 필터를 통과해야 한다.
    @Test
    void extract_cachedEntryContainsConceptSkills_filtersOnReadAndNeverCallsLlm() {
        JobPosting jobPosting = pasuAiJobPosting();
        JobRequirementExtractionEntity cachedEntity = new JobRequirementExtractionEntity(jobPosting, new JobRequirementExtraction(
                List.of("Java", "웹 보안", "동시성 프로그래밍", "AI"),
                List.of("LLM", "React", "생성형 AI"),
                null, null, true,
                List.of(),
                List.of(),
                JobCategory.BACKEND_DEVELOPMENT
        ), JobRequirementExtractor.EXTRACTION_PROMPT_VERSION);
        JobRequirementExtractionRepository repository = Mockito.mock(JobRequirementExtractionRepository.class);
        Mockito.when(repository.findByJobPostingId(Mockito.any())).thenReturn(Optional.of(cachedEntity));
        JobRequirementExtractor extractor = new JobRequirementExtractor(
                (systemPrompt, userPrompt) -> {
                    throw new IllegalStateException("캐시 적중 시 LLM을 호출하면 안 된다");
                },
                jobRequirementParser,
                objectMapper,
                repository
        );

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.requiredSkills()).containsExactly("Java");
        assertThat(extraction.preferredSkills()).containsExactly("React");
    }

    // 기술이 빈 결과도 캐시해야 평가마다 LLM을 다시 부르지 않는다. (국민은행 IT·토스페이먼츠 SRE·한국투자증권 공고)
    @Test
    void extract_noSkillsForDevelopmentJob_cachesResult() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"BACKEND_DEVELOPMENT\""), repository);

        JobRequirementExtraction extraction = extractor.extract(pasuAiJobPosting());

        assertThat(extraction.requiredSkills()).isEmpty();
        assertThat(extraction.jobCategory()).isEqualTo(JobCategory.BACKEND_DEVELOPMENT);
        Mockito.verify(repository).save(Mockito.any(JobRequirementExtractionEntity.class));
    }

    // null은 판단 근거 부족을 뜻하는 정상 응답이므로 캐시한다.
    @Test
    void extract_nullJobCategory_cachesResult() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("null"), repository);

        JobRequirementExtraction extraction = extractor.extract(pasuAiJobPosting());

        assertThat(extraction.jobCategory()).isNull();
        Mockito.verify(repository).save(Mockito.any(JobRequirementExtractionEntity.class));
    }

    // 한국언론진흥재단(55219318)처럼 제목·본문에 IT 키워드가 있는 공고가 OTHER로 나오면 null로 낮춰 저장한다.
    @Test
    void extract_otherWithItKeyword_savesNullJobCategory() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"OTHER\""), repository);

        JobRequirementExtraction extraction = extractor.extract(kpfJobPosting());

        assertThat(extraction.jobCategory()).isNull();
        assertThat(savedEntity(repository).getJobCategory()).isNull();
    }

    // IT 키워드가 없더라도 기술이 추출됐다면 비IT 직무라는 OTHER와 모순되므로 null로 낮춘다.
    @Test
    void extract_otherWithSkills_savesNullJobCategory() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        String llmResponse = """
                {"requiredSkills": ["SQL"], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "OTHER"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse, repository);

        JobRequirementExtraction extraction = extractor.extract(shillaStayJobPosting());

        assertThat(extraction.jobCategory()).isNull();
        assertThat(savedEntity(repository).getJobCategory()).isNull();
    }

    // 신라스테이 호텔 공고처럼 IT 키워드도 기술도 없는 순수 비IT 공고는 OTHER를 그대로 둔다.
    @Test
    void extract_otherForNonItPosting_keepsOther() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"OTHER\""), repository);

        JobRequirementExtraction extraction = extractor.extract(shillaStayJobPosting());

        assertThat(extraction.jobCategory()).isEqualTo(JobCategory.OTHER);
        assertThat(savedEntity(repository).getJobCategory()).isEqualTo(JobCategory.OTHER);
    }

    // 프롬프트 버전이 다른 캐시는 무시하고 재추출하며, 유니크 제약 충돌 없이 같은 행을 덮어쓴다.
    @Test
    void extract_cachedWithOldPromptVersion_reextractsAndOverwritesSameRow() {
        JobPosting jobPosting = pasuAiJobPosting();
        JobRequirementExtractionEntity staleEntity = new JobRequirementExtractionEntity(jobPosting,
                new JobRequirementExtraction(List.of(), List.of(), null, null, true, List.of(), List.of(), JobCategory.OTHER),
                JobRequirementExtractor.EXTRACTION_PROMPT_VERSION - 1);
        JobRequirementExtractionRepository repository = Mockito.mock(JobRequirementExtractionRepository.class);
        Mockito.when(repository.findByJobPostingId(Mockito.any())).thenReturn(Optional.of(staleEntity));
        String llmResponse = """
                {"requiredSkills": ["Java"], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": ["정보처리기사"], "jobCategory": "BACKEND_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse, repository);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.jobCategory()).isEqualTo(JobCategory.BACKEND_DEVELOPMENT);
        Mockito.verify(repository).save(Mockito.same(staleEntity));
        assertThat(staleEntity.getJobCategory()).isEqualTo(JobCategory.BACKEND_DEVELOPMENT);
        assertThat(staleEntity.getRequiredSkills()).isEqualTo("Java");
        assertThat(staleEntity.getPreferredCertificates()).isEqualTo("정보처리기사");
        assertThat(staleEntity.isExtractedWith(JobRequirementExtractor.EXTRACTION_PROMPT_VERSION)).isTrue();
    }

    // prompt_version 컬럼 추가 이전에 저장된 행(null)도 버전 불일치로 보고 재추출한다.
    @Test
    void extract_cachedWithoutPromptVersion_reextracts() {
        JobPosting jobPosting = pasuAiJobPosting();
        JobRequirementExtractionEntity legacyEntity = new JobRequirementExtractionEntity(jobPosting,
                new JobRequirementExtraction(List.of(), List.of(), null, null, true, List.of(), List.of(), JobCategory.OTHER),
                JobRequirementExtractor.EXTRACTION_PROMPT_VERSION);
        ReflectionTestUtils.setField(legacyEntity, "promptVersion", null);
        JobRequirementExtractionRepository repository = Mockito.mock(JobRequirementExtractionRepository.class);
        Mockito.when(repository.findByJobPostingId(Mockito.any())).thenReturn(Optional.of(legacyEntity));
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"BACKEND_DEVELOPMENT\""), repository);

        JobRequirementExtraction extraction = extractor.extract(jobPosting);

        assertThat(extraction.jobCategory()).isEqualTo(JobCategory.BACKEND_DEVELOPMENT);
        Mockito.verify(repository).save(Mockito.same(legacyEntity));
    }

    // 강제 재추출은 현재 버전으로 캐시돼 있어도 LLM을 다시 호출하고 같은 행을 덮어쓰며, 변경 전/후를 함께 돌려준다.
    @Test
    void reExtract_cachedWithCurrentPromptVersion_callsLlmAndOverwritesSameRow() {
        JobPosting jobPosting = pasuAiJobPosting();
        JobRequirementExtractionEntity cachedEntity = new JobRequirementExtractionEntity(jobPosting,
                new JobRequirementExtraction(List.of(), List.of("Java"), null, null, true, List.of(), List.of(), JobCategory.BACKEND_DEVELOPMENT),
                JobRequirementExtractor.EXTRACTION_PROMPT_VERSION);
        JobRequirementExtractionRepository repository = Mockito.mock(JobRequirementExtractionRepository.class);
        Mockito.when(repository.findByJobPostingId(Mockito.any())).thenReturn(Optional.of(cachedEntity));
        AtomicInteger llmCalls = new AtomicInteger();
        JobRequirementExtractor extractor = new JobRequirementExtractor(
                (systemPrompt, userPrompt) -> {
                    llmCalls.incrementAndGet();
                    return """
                            {"requiredSkills": ["AWS"], "preferredSkills": ["Java"], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
                            """;
                },
                jobRequirementParser,
                objectMapper,
                repository
        );

        JobRequirementReExtraction result = extractor.reExtract(jobPosting);

        assertThat(llmCalls.get()).isEqualTo(1);
        assertThat(result.before().requiredSkills()).isEmpty();
        assertThat(result.after().requiredSkills()).containsExactly("AWS");
        Mockito.verify(repository).save(Mockito.same(cachedEntity));
        assertThat(cachedEntity.getRequiredSkills()).isEqualTo("AWS");
    }

    @Test
    void reExtract_noCache_returnsNullBeforeAndSavesNewRow() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"BACKEND_DEVELOPMENT\""), repository);

        JobRequirementReExtraction result = extractor.reExtract(pasuAiJobPosting());

        assertThat(result.before()).isNull();
        assertThat(result.after().jobCategory()).isEqualTo(JobCategory.BACKEND_DEVELOPMENT);
        Mockito.verify(repository).save(Mockito.any(JobRequirementExtractionEntity.class));
    }

    // 재추출은 fallback으로 대체하지 않고 실패를 드러내며, 기존 캐시를 건드리지 않는다.
    @Test
    void reExtract_llmFails_throwsWithoutTouchingCache() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = new JobRequirementExtractor(
                (systemPrompt, userPrompt) -> {
                    throw new IllegalStateException("OpenAI 장애");
                },
                jobRequirementParser,
                objectMapper,
                repository
        );

        assertThatThrownBy(() -> extractor.reExtract(pasuAiJobPosting()))
                .isInstanceOf(LlmResponseException.class);
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    // strict 모드는 모든 프로퍼티가 required여야 하고, jobCategory는 enum 값 또는 null로 제한되어야 한다.
    @Test
    @SuppressWarnings("unchecked")
    void extract_callsLlmWithStrictSchemaCoveringAllFields() {
        AtomicReference<JsonSchemaFormat> capturedSchema = new AtomicReference<>();
        LlmClient schemaCapturingClient = new LlmClient() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                throw new IllegalStateException("추출 호출은 스키마를 함께 넘겨야 한다");
            }

            @Override
            public String complete(String systemPrompt, String userPrompt, JsonSchemaFormat responseSchema) {
                capturedSchema.set(responseSchema);
                return emptySkillsResponse("\"OTHER\"");
            }
        };
        JobRequirementExtractor extractor = new JobRequirementExtractor(
                schemaCapturingClient, jobRequirementParser, objectMapper, neverCachedExtractionRepository());

        extractor.extract(pasuAiJobPosting());

        Map<String, Object> schema = capturedSchema.get().schema();
        List<String> recordFields = Arrays.stream(JobRequirementExtraction.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        Map<String, Object> jobCategory = (Map<String, Object>) properties.get("jobCategory");

        assertThat(schema.get("additionalProperties")).isEqualTo(false);
        assertThat((List<String>) schema.get("required")).containsExactlyInAnyOrderElementsOf(recordFields);
        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(recordFields);
        List<String> expectedJobCategoryValues = new ArrayList<>(Arrays.stream(JobCategory.values()).map(Enum::name).toList());
        expectedJobCategoryValues.add(null);
        assertThat((List<String>) jobCategory.get("type")).containsExactly("string", "null");
        assertThat((List<String>) jobCategory.get("enum")).containsExactlyElementsOf(expectedJobCategoryValues);
    }

    private String emptySkillsResponse(String jobCategoryJson) {
        return """
                {"requiredSkills": [], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": %s}
                """.formatted(jobCategoryJson);
    }

    private JobRequirementExtractionEntity savedEntity(JobRequirementExtractionRepository repository) {
        ArgumentCaptor<JobRequirementExtractionEntity> captor = ArgumentCaptor.forClass(JobRequirementExtractionEntity.class);
        Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private JobRequirementExtractor extractorWith(String llmResponse) {
        return extractorWith(llmResponse, neverCachedExtractionRepository());
    }

    private JobRequirementExtractor extractorWith(String llmResponse, JobRequirementExtractionRepository repository) {
        return new JobRequirementExtractor(
                (systemPrompt, userPrompt) -> llmResponse,
                jobRequirementParser,
                objectMapper,
                repository
        );
    }

    private JobRequirementExtractionRepository neverCachedExtractionRepository() {
        JobRequirementExtractionRepository repository = Mockito.mock(JobRequirementExtractionRepository.class);
        Mockito.when(repository.findByJobPostingId(Mockito.any())).thenReturn(Optional.empty());
        return repository;
    }

    private JobPosting pasuAiJobPosting() {
        return new JobPosting(
                JobSource.SARAMIN,
                "55234023",
                "(주)파수에이아이",
                "신입공채 - 서버 / 데이터 및 AI 보안 솔루션 (백엔드, 프론트엔드)",
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=55234023",
                "서울",
                "신입",
                "대졸(4년) 이상(예정자가능)",
                "Java,Spring,RDBMS,SQL,React,Redux,TypeScript,HTTP,MSA,웹보안,CORS,동시성",
                """
                [직무: 서버 / 1. 데이터 및 AI 보안 솔루션 (백엔드, 프론트엔드)] - 파수 AI 채용

                [담당업무]
                - Java 기반 백엔드 프로그램 설계·개발
                - 마이크로서비스 아키텍처 백엔드 설계·개발

                [주요 기술]
                Java, Spring Framework, RDBMS/SQL, React.js, Redux, TypeScript, HTTP, 웹 보안 (SSL, XSS, CORS), 비동기 프로그래밍, 데이터 시각화, 동시성 프로그래밍, 네트워크 통신 프로그래밍
                """,
                CloseType.FIXED_DATE,
                LocalDateTime.parse("2026-10-25T23:59:59")
        );
    }

    private JobPosting kpfJobPosting() {
        return new JobPosting(
                JobSource.SARAMIN,
                "55219318",
                "한국언론진흥재단",
                "2026년 2차 정규직 신입사원 채용 공고 (IT)",
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=55219318",
                "서울 중구",
                "신입",
                "학력무관",
                "SQL,ERP,그룹웨어,시스템운영,모니터링,데이터베이스,백업복구,장애처리",
                """
                [모집분야] IT(시스템 운영), 정규직

                [직무수행내용]
                - 업무전산시스템(그룹웨어, ERP) 고도화 등 정보시스템 개발 및 운영 관련 업무

                [가점사항]
                - (자격증) 정보처리기사, 정보처리산업기사
                """,
                CloseType.FIXED_DATE,
                LocalDateTime.parse("2026-10-20T23:59:59")
        );
    }

    private JobPosting shillaStayJobPosting() {
        return new JobPosting(
                JobSource.SARAMIN,
                "test-shilla-stay",
                "신라에이치엠(주)",
                "신라스테이 호텔분야 신입채용 (더스타팩토리프로그램)",
                "https://example.com",
                "서울",
                "신입/경력",
                "학력무관",
                "호텔,프론트,객실관리",
                """
                [모집분야] 호텔 프론트·객실 관리

                [주요업무]
                - 고객 응대 및 체크인/체크아웃
                - 객실 관리
                """,
                CloseType.FIXED_DATE,
                LocalDateTime.parse("2026-08-10T23:59:59")
        );
    }

    private JobPosting linkAlphaJobPosting() {
        return new JobPosting(
                JobSource.SARAMIN,
                "54428738",
                "(주)링크알파코리아",
                "Backend Engineer (Agent Platform)",
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=54428738",
                "서울 강남구 강남대로 308, 2109호",
                "경력 5년 이상(또는 이에 준하는 실력)",
                "학력무관",
                "Python,TypeScript,LLM,AI Agent,MCP,Claude Code,Cursor,PostgreSQL,AWS,Azure,GCP,React,Next.js",
                """
                [주요업무]
                - AI 에이전트를 설계, 구현, 운영
                - MCP 도구, 실패 처리, 재시도, 관측 가능성까지 책임

                [자격요건]
                - 백엔드 개발 경력 5년 이상 또는 이에 준하는 실력을 갖춘 분
                - Python과 TypeScript를 활용해 백엔드를 만들 수 있는 분
                - LLM 또는 AI 에이전트를 실제 프로덕션 환경에서 빌드하고 운영해본 분
                - PostgreSQL 등 RDBMS와 비동기 처리, 큐 기반 아키텍처에 대한 실무 이해가 있는 분
                - AWS, Azure, GCP 중 하나 이상에서 프로덕션 서비스를 배포하고 운영해본 분
                """,
                CloseType.FIXED_DATE,
                LocalDateTime.parse("2026-08-15T23:59:59")
        );
    }

    private JobPosting wonTechStopJobPosting() {
        return new JobPosting(
                JobSource.SARAMIN,
                "55205788",
                "원테크스탑 한국지점",
                "IT엔지니어 부문/Web Application 개발&운영",
                "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=55205788",
                "서울 서초구",
                "신입·경력",
                "대졸(4년) 이상(예정자가능)",
                "Java,Python,Node.js,NestJS,Next.js,React,JSP,PostgreSQL,Oracle,GCP,Git,CI/CD",
                """
                [직무: Application Developer (Frontend/Backend Developer)]

                [자격요건]
                - 4년제 대학교 졸업자 (졸업예정자 가능)
                - 영어 가능자 (TOEIC 850 이상)

                [우대사항]
                - 핵심 기술 스택 (아래 기술 중 하나 이상 활용 경험 보유자)
                  - Frontend: NextJs, Reactjs, Jsp 등
                  - Backend: Nestjs, Nodejs, Java, Python 등
                  - DB: Postgresql, Oracle 등 관계형 데이터베이스
                  - Cloud(GCP) 활용 경험 또는 Git(Github), CI/CD에 대한 기본적인 이해
                """,
                CloseType.FIXED_DATE,
                LocalDateTime.parse("2026-10-19T23:59:59")
        );
    }
}
