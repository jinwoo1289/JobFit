package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.entity.JobRequirementExtractionEntity;
import com.jinwoo.jobfit.domain.evaluation.repository.JobRequirementExtractionRepository;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

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
                "Java", "Spring Framework", "RDBMS/SQL", "React.js", "Redux", "TypeScript", "HTTP"
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
        ));
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

    // 개발 직무인데 기술이 하나도 없는 결과가 캐시되면 다음 평가부터 LLM을 다시 부르지 않아 빈 결과가 고정된다.
    @Test
    void extract_noSkillsForDevelopmentJob_returnsResultWithoutCaching() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"BACKEND_DEVELOPMENT\""), repository);

        JobRequirementExtraction extraction = extractor.extract(pasuAiJobPosting());

        assertThat(extraction.requiredSkills()).isEmpty();
        assertThat(extraction.jobCategory()).isEqualTo(JobCategory.BACKEND_DEVELOPMENT);
        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    @Test
    void extract_noSkillsWithNullJobCategory_returnsResultWithoutCaching() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("null"), repository);

        extractor.extract(pasuAiJobPosting());

        Mockito.verify(repository, Mockito.never()).save(Mockito.any());
    }

    // OTHER는 비개발 직무라 기술이 없는 것이 정상이므로 캐시한다.
    @Test
    void extract_noSkillsForOtherJobCategory_cachesResult() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        JobRequirementExtractor extractor = extractorWith(emptySkillsResponse("\"OTHER\""), repository);

        extractor.extract(pasuAiJobPosting());

        Mockito.verify(repository).save(Mockito.any(JobRequirementExtractionEntity.class));
    }

    @Test
    void extract_onlyPreferredSkillsPresent_cachesResult() {
        JobRequirementExtractionRepository repository = neverCachedExtractionRepository();
        String llmResponse = """
                {"requiredSkills": [], "preferredSkills": ["Kotlin"], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": "BACKEND_DEVELOPMENT"}
                """;
        JobRequirementExtractor extractor = extractorWith(llmResponse, repository);

        extractor.extract(pasuAiJobPosting());

        Mockito.verify(repository).save(Mockito.any(JobRequirementExtractionEntity.class));
    }

    private String emptySkillsResponse(String jobCategoryJson) {
        return """
                {"requiredSkills": [], "preferredSkills": [], "minYears": null, "maxYears": null, "newGraduateAllowed": true, "requiredCertificates": [], "preferredCertificates": [], "jobCategory": %s}
                """.formatted(jobCategoryJson);
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
