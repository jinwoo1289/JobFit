package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.entity.JobRequirementExtractionEntity;
import com.jinwoo.jobfit.domain.evaluation.repository.JobRequirementExtractionRepository;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRequirementExtractor {

    private static final String SYSTEM_PROMPT = """
            너는 채용 공고 원문을 읽고 지원 자격 요건을 구조화된 JSON으로 추출하는 어시스턴트다.
            반드시 아래 스키마를 따르는 JSON만 출력하라. 마크다운 코드블록이나 설명 문구는 절대 출력하지 마라.

            {
              "requiredSkills": string[],
              "preferredSkills": string[],
              "minYears": number | null,
              "maxYears": number | null,
              "newGraduateAllowed": boolean,
              "requiredCertificates": string[],
              "jobCategory": "BACKEND_DEVELOPMENT" | "FRONTEND_DEVELOPMENT" | "FULLSTACK_DEVELOPMENT" | "MOBILE_DEVELOPMENT" | "DATA_ENGINEERING" | "AI_ML" | "DEVOPS" | "QA" | "OTHER"
            }

            [requiredSkills / preferredSkills 규칙]
            - requiredSkills와 preferredSkills는 오직 원문 섹션 위치로만 구분하라.
              "자격요건", "필수 요건", "주요업무"처럼 필수 자격/업무를 다루는 섹션에 명시된 기술만 requiredSkills에 넣어라.
              "우대사항", "자격 및 우대사항", "~하면 좋음", "~우대"처럼 우대를 다루는 섹션에 나열된 기술은, 그 문구에 "필수"라는 표현이 붙어 있더라도 requiredSkills로 옮기지 말고 preferredSkills로 분류하라.
              (requiredCertificates의 "필수 표기 시 섹션 무관하게 필수로 판단" 규칙은 자격증·어학성적에만 적용되며, 스킬 분류에는 적용하지 않는다.)
              예: "[자격 및 우대사항]" 섹션에 "GitHub Copilot, Claude Code, Jira, Bitbucket, Confluence" 도구 목록과 "OPIC 제출 필수" 문구가 함께 있어도, 도구 목록은 preferredSkills로 분류하고 requiredSkills로 옮기지 마라.
            - 반드시 기술·도구·언어·프레임워크의 이름만 담아라. 단어 또는 짧은 고유명사여야 한다.
            - 요구사항을 설명하는 문장이나 서술을 절대 넣지 마라.
              예: "Python 또는 백엔드 개발 경험" (X) → "Python" (O)
              예: "웹 서비스 및 RESTful API에 대한 기본 이해" (X) → "REST API" (O)
            - 방법론·개념·아키텍처 스타일 용어는 구체적인 기술/도구 이름이 아니므로 절대 포함하지 마라.
              절대 금지 목록(원문에 그대로 등장하더라도 출력에 포함하면 안 됨): MDD, MSA, Microservices, 마이크로서비스, 모놀리식, Monolithic, DDD, 도메인 주도 설계, 이벤트 기반 아키텍처, Event-Driven, TDD, BDD, 애자일, Agile, 스크럼, Scrum, 워터폴, CI/CD, DevOps,
              비동기 처리, 큐 기반 아키텍처, RDBMS, 관계형 데이터베이스, 객체지향
              (같은 개념의 한글/영문 표기, 축약어/풀네임 모두 동일하게 제외 대상이다. 프로그래밍 언어, 프레임워크, 라이브러리, 데이터베이스, 클라우드 서비스, 자격증, 협업 툴이 아니라면 넣지 마라)
            - 여러 기술을 묶어 지칭하는 카테고리 표현도 절대 포함하지 마라. (예: AI 개발도구, 협업 도구, 클라우드) 카테고리가 아니라 그 안에 언급된 구체적인 제품명만 추출하라.
              예: "AI 개발도구" (X) → 제외, "GitHub Copilot" (O) → 포함
            - 범용 프로토콜·데이터 포맷 이름은 절대 포함하지 마라: HTTP, JSON, XML (단, REST API는 실무에서 스킬로 통용되므로 예외적으로 유지한다)
            - 원문이 한글로 표기되어 있어도 기술명은 영문 표기로 통일하라. (예: "스프링 부트" → "Spring Boot", "자바" → "Java")
            - requiredSkills와 preferredSkills를 채운 뒤, 목록에 방법론/개념어/카테고리 표현/범용 프로토콜이 섞여 있지 않은지 다시 한번 스스로 검토하고 있다면 제거한 뒤 최종 JSON을 출력하라.

            [requiredCertificates 규칙]
            - 이 규칙은 자격증·어학성적에만 적용된다. requiredSkills / preferredSkills 분류에는 영향을 주지 않는다.
            - 자격증·어학성적 요구는 원문 내 섹션 위치(우대사항, 필수 자격 등)와 무관하게, 문구 자체의 표현을 기준으로 필수 여부를 판단하라.
            - "필수", "제출 필수", "반드시"처럼 필수임을 나타내는 표현이 붙어 있다면, "우대사항" 섹션에 있더라도 requiredCertificates에 포함하라.
            - 자격증/어학성적의 명칭만 추출하고, 기간·점수 제출 방식 등 부가 설명은 제외하라.
              예: "[자격 및 우대사항]" 섹션의 "OPIC 또는 TOEIC Speaking 점수 제출 필수(2년 내)"
                  → requiredCertificates: ["OPIC", "TOEIC Speaking"]

            [minYears / maxYears 규칙]
            - "경력 N년 이상"처럼 하한만 명시된 경우에만 minYears에 N을 넣어라.
            - "N년 이하"처럼 상한이 명시된 경우 maxYears에 N을 넣어라. 이 경우 minYears는 null로 둬라.
            - 범위가 명시되지 않으면 해당 필드를 null로 둬라.
            """;

    private final LlmClient llmClient;
    private final JobRequirementParser jobRequirementParser;
    private final ObjectMapper objectMapper;
    private final JobRequirementExtractionRepository jobRequirementExtractionRepository;

    // 외부(EvaluationQueryService 등)의 readOnly 트랜잭션 안에서 호출되더라도 캐시 저장(INSERT)이
    // 항상 성공하도록 별도의 쓰기 가능한 트랜잭션에서 실행한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobRequirementExtraction extract(JobPosting jobPosting) {
        return jobRequirementExtractionRepository.findByJobPostingId(jobPosting.getId())
                .map(JobRequirementExtractionEntity::toExtraction)
                .orElseGet(() -> callLlmAndCache(jobPosting));
    }

    private JobRequirementExtraction callLlmAndCache(JobPosting jobPosting) {
        try {
            String response = llmClient.complete(SYSTEM_PROMPT, buildUserPrompt(jobPosting));
            JobRequirementExtraction extraction = objectMapper.readValue(response, JobRequirementExtraction.class);
            jobRequirementExtractionRepository.save(new JobRequirementExtractionEntity(jobPosting, extraction));
            return extraction;
        } catch (RuntimeException e) {
            // fallback 결과는 저장하지 않는다. LLM 장애가 해소되면 다음 평가에서 다시 시도하도록 하기 위함.
            log.warn("LLM 추출 실패, 정규식 fallback 사용: {}", e.getMessage(), e);
            return fallback(jobPosting);
        }
    }

    private String buildUserPrompt(JobPosting jobPosting) {
        return """
                [공고 제목]
                %s

                [경력 조건]
                %s

                [상세 내용]
                %s
                """.formatted(
                nullToEmpty(jobPosting.getTitle()),
                nullToEmpty(jobPosting.getExperienceLevel()),
                nullToEmpty(jobPosting.getDescription())
        );
    }

    private JobRequirementExtraction fallback(JobPosting jobPosting) {
        JobRequirement requirement = jobRequirementParser.parse(jobPosting);
        return new JobRequirementExtraction(
                List.of(),
                List.of(),
                requirement.minYears(),
                requirement.maxYears(),
                requirement.newGraduateAllowed(),
                List.of(),
                null
        );
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
