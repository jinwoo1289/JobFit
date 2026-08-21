package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.repository.JobRequirementExtractionRepository;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationVerdict;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.job.entity.JobSource;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EvaluationServiceTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 8, 6, 0, 0, 0).atZone(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault()
    );

    // JobRequirementExtractor가 LLM 대신 이 표를 참조하도록 FakeLlmClient에 등록한다.
    // (기존 keywords 기반 테스트와 동일한 requiredSkills를 재현해 스킬 점수 기대값을 그대로 유지)
    private static final Map<String, String> REQUIRED_SKILLS_BY_TITLE = Map.ofEntries(
            Map.entry("보안 연구소 솔루션 개발자(신입)", "Python,Django,Docker,Linux,PostgreSQL,Redis,FastAPI,SQL"),
            Map.entry("백엔드 개발자/디자인그룹 경력 및 신입사원 모집", "Python,FastAPI,PostgreSQL,SQLAlchemy,REST API,Git,Linux,React,Next.js"),
            Map.entry("[OCI계열사] OCI정보통신 사내 솔루션 개발 및 기능 개선 담당", "React,Java,Spring Boot,Generative AI,AI Coding Assistant,Jira,Bitbucket,Confluence"),
            Map.entry("Backend Engineer (Agent Platform)", "Python,TypeScript,LLM,AI Agent,MCP,Claude Code,Cursor,PostgreSQL,AWS,Azure,GCP,React,Next.js"),
            Map.entry("[NHN Cloud]스토리지 엔진 개발", "C,C++,Go,Python"),
            Map.entry("신라스테이 호텔분야 신입채용 (더스타팩토리프로그램)", ""),
            Map.entry("[인턴] Product Engineer_Backend (연계형)", "Kotlin,Spring Boot,Spring Batch,QueryDSL,JPA,Node.js,JavaScript(ES6+),Nest.js,TypeORM,Python,PostgreSQL,MySQL,Redis,Apache Kafka,Airflow,AWS,Github,Slack,Jira,Notion"),
            Map.entry("Server Developer(여신)", "Java,Kotlin,Spring Boot,MSA,코어뱅킹,MDD")
    );

    private final JobRequirementExtractor jobRequirementExtractor = new JobRequirementExtractor(
            new FakeLlmClient(REQUIRED_SKILLS_BY_TITLE),
            new JobRequirementParser(),
            new ObjectMapper(),
            neverCachedExtractionRepository()
    );

    // FakeLlmClient는 근거 생성 프롬프트에도 재사용되지만, requiredSkills 스키마를 반환하므로
    // EvaluationReasoning 파싱에는 항상 실패해 규칙 기반 fallback 경로를 타게 된다.
    private final EvaluationService evaluationService = new EvaluationService(
            new RequiredConditionChecker(new JobRequirementParser(), FIXED_CLOCK),
            new ScoreCalculator(new SkillScoreCalculator(), new ExperienceScoreCalculator(), new CertificateScoreCalculator()),
            jobRequirementExtractor,
            new EvaluationReasoningGenerator(new FakeLlmClient(REQUIRED_SKILLS_BY_TITLE), new ObjectMapper())
    );

    @ParameterizedTest(name = "[{index}] {0} - verdict={2}")
    @MethodSource("seedJobPostings")
    void evaluate_seedJobPosting(String companyName, JobPosting jobPosting, EvaluationVerdict expectedVerdict,
                                  double expectedTotalScore, double expectedSkillScore, double expectedExperienceScore) {
        EvaluationResult result = evaluationService.evaluate(userProfile(), skills(), projects(), certificates(), evaluationWeight(), jobPosting);

        assertThat(result.verdict()).isEqualTo(expectedVerdict);
        assertThat(result.totalScore()).isCloseTo(expectedTotalScore, within(0.001));
        assertThat(result.skillScore()).isCloseTo(expectedSkillScore, within(0.001));
        assertThat(result.experienceScore()).isCloseTo(expectedExperienceScore, within(0.001));
        // FakeLlmClient는 preferredSkills/requiredCertificates를 항상 비워 반환하므로 두 점수 모두 0이어야 함
        assertThat(result.preferenceScore()).isEqualTo(0.0);
        assertThat(result.certificateScore()).isEqualTo(0.0);
    }

    @Test
    void evaluate_unfitJobPosting_carriesFailedReasonsButStillExposesRealScores() {
        // 링크알파코리아는 경력 5년 이상 요건 미충족으로 UNFIT이지만,
        // 필수조건과 무관하게 실제 스킬/경험 점수는 그대로 계산되어 노출되어야 함 (totalScore만 0)
        JobPosting jobPosting = jobPostingOf("링크알파코리아");

        EvaluationResult result = evaluationService.evaluate(userProfile(), skills(), projects(), certificates(), evaluationWeight(), jobPosting);

        assertThat(result.verdict()).isEqualTo(EvaluationVerdict.UNFIT);
        assertThat(result.failedReasons()).isNotEmpty();
        assertThat(result.totalScore()).isEqualTo(0.0);
        assertThat(result.skillScore()).isCloseTo(100.0 / 13, within(0.001));
        assertThat(result.experienceScore()).isCloseTo(100.0 / 13, within(0.001));
    }

    @Test
    void evaluate_seed8JobPostings_ranksTossBankFirstAmongFitPostings() {
        record Ranked(String companyName, EvaluationResult result) {
        }

        List<Ranked> ranked = seedJobPostings()
                .map(args -> new Ranked(
                        (String) args.get()[0],
                        evaluationService.evaluate(userProfile(), skills(), projects(), certificates(), evaluationWeight(), (JobPosting) args.get()[1])
                ))
                .filter(entry -> entry.result().verdict() == EvaluationVerdict.FIT)
                .sorted(Comparator.comparingDouble((Ranked entry) -> entry.result().totalScore()).reversed())
                .toList();

        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).companyName()).isEqualTo("토스뱅크");
    }

    private JobPosting jobPostingOf(String companyName) {
        return seedJobPostings()
                .filter(args -> args.get()[0].equals(companyName))
                .findFirst()
                .map(args -> (JobPosting) args.get()[1])
                .orElseThrow();
    }

    static Stream<Arguments> seedJobPostings() {
        return Stream.of(
                Arguments.of("위드네트웍스", jobPosting(
                        "보안 연구소 솔루션 개발자(신입)", "신입",
                        "Python,Django,Docker,Linux,PostgreSQL,Redis,FastAPI,SQL",
                        "[주요업무]\n- AI 기반 개발 환경을 활용한 서비스 설계 및 구현\n- 보안 솔루션 및 백엔드 시스템 개발\n- AI를 활용한 코드 분석, 테스트 자동화 및 품질 개선\n- 외부 시스템 및 보안 도구 연동\n- 고객사 요구사항 분석 및 신규 기능·커스터마이징 개발\n- 신규 기술 검토 및 AI 기반 개발 프로세스 개선\n\n[자격요건]\n- Python 또는 백엔드 개발 경험\n- AI 개발 도구를 활용한 개발에 관심이 있고 적극적으로 활용할 수 있는 분\n- 웹 서비스 및 RESTful API에 대한 기본 이해\n- SQL 및 관계형 데이터베이스에 대한 기본 이해\n- 새로운 기술을 빠르게 학습하고 적용할 수 있는 분\n- 원활한 커뮤니케이션 및 협업이 가능한 분\n\n[우대사항]\n- AI 개발 도구(Claude, ChatGPT, GitHub Copilot, Cursor 등) 활용 경험\n- Python 및 FastAPI, Django 등 백엔드 프레임워크 개발 경험\n- PostgreSQL, Redis, Docker, Linux 등 개발 환경 경험\n- Git 기반 협업 및 형상관리 경험\n- AI를 활용한 개발 프로세스 개선 또는 자동화 경험",
                        "2026-08-26T23:59:59"
                ), EvaluationVerdict.FIT, 13.75, 25.0, 12.5),
                Arguments.of("킨코스코리아", jobPosting(
                        "백엔드 개발자/디자인그룹 경력 및 신입사원 모집", "신입/경력 3년 이하",
                        "Python,FastAPI,PostgreSQL,SQLAlchemy,REST API,Git,Linux,React,Next.js",
                        "[담당업무]\n- Python·FastAPI 기반 사내 업무시스템 개발 및 유지보수\n- PostgreSQL 데이터 조회·처리 및 API 개발\n- 기능 개선, 버그 수정 및 테스트\n- 외부 시스템 데이터 연동 및 장애 분석\n- 현업 요구사항 분석 및 업무 프로세스 개선\n\n[자격요건]\n- 신입 또는 경력 3년 이하\n- Python 또는 Java 개발 경험\n- SQL 및 관계형 데이터베이스 기본 이해\n- REST API, HTTP, JSON 기본 이해\n- Git 사용 경험\n\n[우대사항]\n- FastAPI, Django, Spring Boot 경험\n- PostgreSQL, MySQL 사용 경험\n- React·TypeScript 또는 API 연동 경험\n- Docker·Linux 환경 경험\n- 업무 자동화 또는 사내 시스템 개발 경험\n- AI 개발도구 활용 경험 및 코드 검증 역량\n\n[주요 기술]\n- Python, FastAPI\n- PostgreSQL\n- SQLAlchemy\n- REST API\n- Git\n- Linux\n- React, Next.js (기본 이해 수준)",
                        "2026-08-07T23:59:59"
                ), EvaluationVerdict.FIT, 0.0, 0.0, 0.0),
                Arguments.of("OCI정보통신", jobPosting(
                        "[OCI계열사] OCI정보통신 사내 솔루션 개발 및 기능 개선 담당", "신입/경력 2년 이하",
                        "React,Java,Spring Boot,Generative AI,AI Coding Assistant,Jira,Bitbucket,Confluence",
                        "[업무 내용]\n- AI 기반 ERP 시스템 개발\n- React 및 Java Spring Boot 기반 ERP 기능 개선 및 고도화\n- AI 코딩(Copilot, Claude Code)을 활용한 빠른 단위 기능 구현 및 코드 리팩토링\n- 장애 대응, 데이터 처리 및 프로젝트 지원 및 외주 업체와 협업\n- 신규 외부 프로젝트 지원 및 기술 소통 및 소스/품질 관리\n\n[학력 / 전공]\n- 대졸 이상 (컴퓨터 소프트웨어 관련 전공자)\n\n[자격 및 우대사항]\n- Web 프론트엔드(React) 및 백엔드(Java/Spring Boot) 기초 지식 보유자\n- AI 도구를 활용해 코드 작성, 오류 수정, 학습을 적극적으로 수행하는 태도\n- 외부 협력사 및 내부 담당자와의 원활한 커뮤니케이션 능력\n- IT 국비지원 부트캠프(6개월 이상) 이수자\n- Generative AI / AI Coding Assistant 실무·프로젝트 적용 경험자\n- 프로젝트 리더로서 팀 프로젝트를 완수한 경험이 있는 분\n- Jira, Bitbucket, Confluence 등 협업/프로젝트 관리 도구 활용 경험자\n- OPIC 또는 TOEIC Speaking 점수 제출 필수 (2년 내)",
                        "2026-09-04T23:59:59"
                ), EvaluationVerdict.FIT, 17.5, 25.0, 25.0),
                Arguments.of("링크알파코리아", jobPosting(
                        "Backend Engineer (Agent Platform)", "경력 5년 이상(또는 이에 준하는 실력)",
                        "Python,TypeScript,LLM,AI Agent,MCP,Claude Code,Cursor,PostgreSQL,AWS,Azure,GCP,React,Next.js",
                        "[주요업무]\n- AI 에이전트를 설계, 구현, 운영. 멀티스텝 추론, 도구 호출 루프, 컨텍스트와 메모리 관리, 토큰, 비용, 지연 시간 최적화까지 담당\n- 에이전트가 사용하는 도구 레이어를 설계하고 구현. MCP 도구, 실패 처리, 재시도, 관측 가능성까지 책임\n- 멀티 프로바이더 LLM 호출을 스트리밍, 캐싱, 폴백 구조와 함께 안정적으로 운영\n- 에이전트의 품질과 실패를 추적하고 디버깅. 트레이싱, 평가, 재현 가능한 테스트를 통해 프로덕션 회귀 없이 개선\n- 에이전트를 떠받치는 백엔드 API, 비동기 잡, 서비스 간 통신 구조 설계\n- 필요할 경우 React / Next.js 기반 제품 화면까지 직접 다루며 기능을 end-to-end로 완성\n- Claude Code, Cursor 등 coding agent를 적극 활용해 본인과 팀의 개발 속도를 높임\n\n[자격요건]\n- 백엔드 개발 경력 5년 이상 또는 이에 준하는 실력을 갖춘 분\n- Python과 TypeScript를 활용해 백엔드를 만들 수 있는 분\n- Python과 TypeScript 중 하나에 능숙하고, 나머지 언어를 학습하고 사용하는 데 거리낌이 없는 분\n- LLM 또는 AI 에이전트를 실제 프로덕션 환경에서 빌드하고 운영해본 분\n- 데모나 토이 프로젝트가 아니라, 실사용자 트래픽을 받는 에이전트를 운영해본 경험이 있는 분\n- 도구 호출 루프, 컨텍스트 관리, 토큰과 비용, 스트리밍, 실패 모드 등 에이전트 동작 원리를 깊이 이해하는 분\n- Claude Code, Cursor 등 Coding agent를 깊이 사용해본 분\n- 무엇을 에이전트에 위임할 수 있고, 어디서 사람이 개입해야 하는지 체득한 분\n- PostgreSQL 등 RDBMS와 비동기 처리, 큐 기반 아키텍처에 대한 실무 이해가 있는 분\n- AWS, Azure, GCP 중 하나 이상에서 프로덕션 서비스를 배포하고 운영해본 분\n- 문제를 주도적으로 발견하고 해결하며, 타 직군과 원활하게 협업할 수 있는 분",
                        "2026-08-15T23:59:59"
                ), EvaluationVerdict.UNFIT, 0.0, 100.0 / 13, 100.0 / 13),
                Arguments.of("엔에이치엔클라우드", jobPosting(
                        "[NHN Cloud]스토리지 엔진 개발", "경력무관(신입포함)",
                        "C,C++,Go,Python",
                        "요구사항 상세 미확인 — 목록 정보 기반",
                        "2026-08-15T23:59:59"
                ), EvaluationVerdict.FIT, 0.0, 0.0, 0.0),
                Arguments.of("신라에이치엠", jobPosting(
                        "신라스테이 호텔분야 신입채용 (더스타팩토리프로그램)", "신입/경력",
                        "",
                        "요구사항 상세 미확인 — 목록 정보 기반",
                        "2026-08-10T23:59:59"
                ), EvaluationVerdict.FIT, 0.0, 0.0, 0.0),
                Arguments.of("누비랩", jobPosting(
                        "[인턴] Product Engineer_Backend (연계형)", "경력무관 / 인턴",
                        "Kotlin,Spring Boot,Spring Batch,QueryDSL,JPA,Node.js,JavaScript(ES6+),Nest.js,TypeORM,Python,PostgreSQL,MySQL,Redis,Apache Kafka,Airflow,AWS,Github,Slack,Jira,Notion",
                        "[주요업무]\n- 제품의 특정 문제 영역을 정의하고 해결하는 End-to-End Ownership을 가지고 문제 정의부터 실험·구현·배포·개선까지 전 과정을 책임\n- 제품의 흐름과 상호작용, 기능 구조를 설계하고 사용자가 실제로 경험하는 제품 경험 전반을 개선\n- AI가 제품 개발 과정에서 최고의 속도로 문제를 해결할 수 있도록 개발 환경과 시스템을 설계하고 불필요한 마찰을 제거\n- 가설을 빠르게 세우고 다양한 방식으로 해법을 실험하며, 실험 결과와 사용자 피드백을 기반으로 제품을 지속적으로 개선\n- PM, 디자이너와 협업하여 문제를 정의하고 해결 방향을 함께 설계\n\n[자격요건]\n- 경력 무관 / 인턴\n- 백엔드 또는 풀스택 개발 경험이 있으신 분\n- 기술 자체보다 제품 문제를 해결하는 과정에 더 큰 흥미를 느끼는 분\n- 문제를 구조적으로 분석하고 빠르게 실험하며 해결할 수 있는 분\n- 새로운 도구와 기술을 빠르게 학습하고 실제 제품 개발에 적용할 수 있는 분\n- 빠르게 만들고, 실험하고, 개선하는 제품 중심 개발 사이클에 익숙한 분\n- AI가 개발 과정에서 효과적으로 활용될 수 있도록 개발 환경과 워크플로우를 개선하는 데 관심이 있는 분\n\n[기술환경]\n- Kotlin, Spring boot, Spring Batch, QueryDSL, JPA, Node.js, Javascript(ES6+), Nest.js, TypeORM, Python\n- PostgreSQL, MySQL, Redis\n- Apache Kafka, Airflow, AWS\n- Github, Slack, Jira, Notion",
                        "2026-08-15T23:59:59"
                ), EvaluationVerdict.UNFIT, 0.0, 23.0, 20.0),
                Arguments.of("토스뱅크", jobPosting(
                        "Server Developer(여신)", "신입/경력",
                        "Java,Kotlin,Spring Boot,MSA,코어뱅킹,MDD",
                        "[주요업무]\n- 토스뱅크의 모든 여신 상품(신용, 기업, 전세 등)에 대한 신청·심사·금리 산출·실행·상환·사후관리 도메인을 각 파트별로 전담\n- MDD(Model Driven Development) + Java 기반 모놀리식 코어뱅킹 시스템을 안정적으로 유지하면서, Kotlin + Spring Boot 기반의 MSA 구조로 단계적으로 전환\n- 영업점 없는 환경에서 대출 시스템 전체 흐름이 사람 없이 이루어질 수 있도록 프로세스를 설계하고 자동화\n- 정책 중심 로직을 도메인 기반 구조로 재해석하고 리팩토링하며, 법령 변화·정책 변경·운영 요구까지 아우를 수 있는 지속 가능한 설계 구조로 발전\n- 고정밀 수치 계산과 실시간 처리 요구를 만족시키는 환경 속에서, 데이터 정합성·트랜잭션 처리·이벤트 연계 등 마이크로서비스 설계의 핵심 요소들을 직접 설계하고 구현\n- 반복되는 정책 분기와 다양한 케이스를 기준화하여 팀 내 설계 기준을 세우고, 확장 가능한 고가용성 구조를 직접 구축\n\n[자격요건]\n- 복잡한 비즈니스 흐름이나 경험해보지 못한 기술 스택도 빠르게 학습하고 흡수하려는 태도를 갖춘 분\n- 문제의 본질을 파악하고, 실질적인 해결에 집중하는 분. 주어진 요구를 그대로 구현하기보다, 스스로 기준을 세우고 마주한 문제를 해결하는 분\n- 유관부서와의 협업에서 소통에 책임감을 가지고, 함께 해법을 만들어가는 분. 변화를 두려워하지 않고 지속적인 성장을 원하는 분\n- 금융권이나 복잡한 정책 기반 시스템에서의 운영·개발 경험이 있다면 더욱 좋음",
                        "2026-08-15T23:59:59"
                ), EvaluationVerdict.FIT, 82.0 / 3, 260.0 / 6, 200.0 / 6)
        );
    }

    private static List<UserSkill> skills() {
        UserProfile profile = userProfile();
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
        UserProfile profile = userProfile();
        return List.of(
                new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스", List.of("Java", "Spring Boot", "JPA", "Redis", "AWS")),
                new UserProject(profile, "KakaoMCP", "MCP 연동 프로젝트", List.of("Java", "Spring Boot", "Docker"))
        );
    }

    private static List<UserCertificate> certificates() {
        return List.of();
    }

    // 이 테스트의 JobPosting은 저장되지 않은(id=null) 상태이므로, 캐시를 항상 miss로 만들어
    // JobRequirementExtractor가 매번 FakeLlmClient를 거치도록 한다.
    private static JobRequirementExtractionRepository neverCachedExtractionRepository() {
        JobRequirementExtractionRepository repository = Mockito.mock(JobRequirementExtractionRepository.class);
        Mockito.when(repository.findByJobPostingId(Mockito.any())).thenReturn(Optional.empty());
        return repository;
    }

    private static EvaluationWeight evaluationWeight() {
        return new EvaluationWeight(userProfile(), 40, 30, 20, 10);
    }

    private static UserProfile userProfile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }

    private static JobPosting jobPosting(String title, String experienceLevel, String keywords,
                                          String description, String expirationAt) {
        return new JobPosting(
                JobSource.SARAMIN,
                "test-external-id",
                "테스트 회사",
                title,
                "https://example.com",
                "서울",
                experienceLevel,
                "학력무관",
                keywords,
                description,
                CloseType.FIXED_DATE,
                LocalDateTime.parse(expirationAt)
        );
    }
}
