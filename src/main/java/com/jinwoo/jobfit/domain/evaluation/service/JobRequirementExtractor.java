package com.jinwoo.jobfit.domain.evaluation.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jinwoo.jobfit.domain.evaluation.client.JsonSchemaFormat;
import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.entity.JobRequirementExtractionEntity;
import com.jinwoo.jobfit.domain.evaluation.exception.LlmResponseException;
import com.jinwoo.jobfit.domain.evaluation.repository.JobRequirementExtractionRepository;
import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.ExperienceLevelType;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirement;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementReExtraction;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class JobRequirementExtractor {

    private static final String SYSTEM_PROMPT = """
            너는 채용 공고 원문을 읽고 지원 자격 요건을 구조화된 JSON으로 추출하는 어시스턴트다.
            반드시 아래 스키마를 따르는 JSON만 출력하라. 마크다운 코드블록이나 설명 문구는 절대 출력하지 마라.

            {
              "requiredSkills": string[],
              "requiredAnyOf": string[][],
              "preferredSkills": string[],
              "minYears": number | null,
              "maxYears": number | null,
              "newGraduateAllowed": boolean,
              "requiredCertificates": string[],
              "preferredCertificates": string[],
              "jobCategory": "BACKEND_DEVELOPMENT" | "FRONTEND_DEVELOPMENT" | "FULLSTACK_DEVELOPMENT" | "MOBILE_DEVELOPMENT" | "DATA_ENGINEERING" | "AI_ML" | "DEVOPS" | "QA" | "IT_GENERAL" | "OTHER" | null
            }

            [jobCategory 판정 규칙]
            - jobCategory는 requiredSkills가 비어 있는 것과 무관하게 독립적으로 판단하라.
            - 공고 제목이나 직무명에 명시된 직무(Backend, Frontend 등)와 [주요업무]의 실제 업무를 기준으로 판단하라.
              회사의 사업 도메인이나 제품 분야(AI, 금융, 커머스 등)로 판단하지 마라.
              예: "Backend Engineer (Agent Platform)"처럼 AI 에이전트 플랫폼 회사의 백엔드 공고 → BACKEND_DEVELOPMENT
            - 각 값은 아래 조건에 해당할 때만 사용하라. enum 이름만 보고 고르지 마라.
              - BACKEND_DEVELOPMENT: 서버·API·데이터 처리 등 백엔드 애플리케이션 설계·개발이 주 업무일 때.
              - FRONTEND_DEVELOPMENT: 웹 화면(UI) 개발이 주 업무일 때.
              - FULLSTACK_DEVELOPMENT: 백엔드와 프론트엔드 개발을 함께 담당하거나 웹 애플리케이션 개발 전반을 담당할 때.
              - MOBILE_DEVELOPMENT: iOS·Android 등 모바일 앱 개발이 주 업무일 때.
              - DATA_ENGINEERING: 데이터 파이프라인·데이터 플랫폼 구축·운영이 주 업무일 때.
              - AI_ML: 모델 학습·평가·추론 최적화 등 ML 자체가 주 업무일 때만 사용하라.
                AI 제품의 서버·API 개발은 BACKEND_DEVELOPMENT로 분류하라.
              - DEVOPS: CI/CD, 배포 자동화, 컨테이너·클라우드 인프라 운영이 주 업무일 때만 사용하라.
                IT 시스템 운영·전산 운영 공고는 DEVOPS로 분류하지 마라.
              - QA: 소프트웨어 테스트·품질 보증이 주 업무일 때.
              - IT_GENERAL: 직무가 IT·전산으로 통합돼 개발/운영이 나뉘지 않은 공채, IT 시스템 운영·전산 운영,
                사내 정보시스템(ERP·그룹웨어 등) 운영이 주 업무일 때.
              - OTHER: 순수 비IT 업무(영업, 심사, 인사, 서비스직 등)만 있을 때만 사용하라.
              - null: 원문에 위 값 중 하나로 판단할 근거가 부족할 때. 근거 없이 가장 비슷해 보이는 값을 고르지 마라.
            - [주요업무] 또는 [상세 내용]에 소프트웨어 설계·개발·운영 업무(예: "~개발 및 운영", "애플리케이션 개발",
              "시스템 구축")가 명시돼 있다면, 원문에 구체적인 기술명(Java, Spring 등)이 등장하지 않더라도
              OTHER로 분류하지 말고 해당 업무 성격에 가장 가까운 카테고리로 분류하라.
            - [등록된 키워드]는 jobCategory 판단에만 참고하라. requiredSkills/preferredSkills는 [상세 내용]에 실제로 등장한 기술만
              섹션 위치 규칙에 따라 넣어라. [상세 내용]에 등장한 기술은 키워드에 같은 이름이 있더라도 제외하지 마라.
              반대로 키워드에만 있고 [상세 내용]에는 없는 기술은 넣지 마라.
            - 여러 직무 트랙이 한 공고에 섞여 있어도, 그중 하나라도 소프트웨어 개발 또는 IT 운영 업무를 명시하면 OTHER를 쓰지 마라.
              순수 비IT 업무(영업, 심사, 인사, 서비스직 등)만 있을 때만 OTHER를 사용하라.
            
            [requiredSkills / preferredSkills 규칙]
            - requiredSkills와 preferredSkills는 오직 원문 섹션 위치로만 구분하라.
              "자격요건", "필수 요건", "주요업무"처럼 필수 자격/업무를 다루는 섹션에 명시된 기술만 requiredSkills에 넣어라.
              "우대사항", "자격 및 우대사항", "~하면 좋음", "~우대"처럼 우대를 다루는 섹션에 나열된 기술은, 그 문구에 "필수"라는 표현이 붙어 있더라도 requiredSkills로 옮기지 말고 preferredSkills로 분류하라.
              [기술환경], [개발환경], [기술 스택], [사용 기술]처럼 회사가 사용하는 기술을 소개하는 별도 섹션에 나열된 기술은
              preferredSkills로 분류하라. requiredSkills로 옮기지 마라. 이 규칙은 그런 섹션 제목 아래 나열된 기술에만 적용되며,
              [주요업무]·[자격요건] 문장 안에서 "~ 기반", "~로 전환"처럼 언급된 기술은 위 규칙대로 requiredSkills로 분류하라.
              (requiredCertificates의 "필수 표기 시 섹션 무관하게 필수로 판단" 규칙은 자격증·어학성적에만 적용되며, 스킬 분류에는 적용하지 않는다.)
              예: "[자격 및 우대사항]" 섹션에 "GitHub Copilot, Claude Code, Jira, Bitbucket, Confluence" 도구 목록과 "OPIC 제출 필수" 문구가 함께 있어도, 도구 목록은 preferredSkills로 분류하고 requiredSkills로 옮기지 마라.
            - [자격요건]에 명시된 기술은 위 섹션 위치 규칙대로 모두 필수로 넣어라. "기본 이해", "경험"처럼 요구 수준이 낮은 표현이어도 마찬가지다.
              단, "또는"처럼 그중 하나만 갖추면 되는 기술은 requiredSkills가 아니라 아래 [requiredAnyOf 규칙]대로 그룹으로 넣어라.
              예: "[자격요건]" 섹션의 "SQL 및 관계형 데이터베이스 기본 이해" → requiredSkills: ["SQL"]
              [담당업무]·[주요업무]에 더 구체적인 기술(예: SQL에 대한 PostgreSQL)이 나와도 [자격요건]의 기술을 중복으로 보지 말고 그대로 넣어라.
              예: [담당업무]에 "PostgreSQL 데이터 조회", [자격요건]에 "SQL 및 관계형 데이터베이스 기본 이해"
                  → requiredSkills에 "PostgreSQL"과 "SQL"을 모두 넣는다.
            - 아래 "~ 기반" 규칙은 [자격요건] 규칙을 대체하지 않고 추가로 적용하는 규칙이다.
              [주요업무]에서 기술을 찾았다고 [자격요건]의 기술을 빼지 마라. 두 섹션의 기술을 모두 requiredSkills에 넣어라.
            - [주요업무]·[담당업무]·[수행업무] 문장 안에 "~ 기반", "~ 환경에서"처럼 업무의 수단·환경으로 언급된 구체적 기술도
              빠뜨리지 말고 requiredSkills에 넣어라. 문장이 기술 사용 요건이 아니라 업무 설명처럼 보여도 마찬가지다.
              예: "[주요업무]" 섹션의 "AWS 기반 서비스 운영 및 개선 활동" → requiredSkills: ["AWS"]
              예: "[담당업무]" 섹션의 "Kubernetes 환경에서 서비스 배포 및 운영" → requiredSkills: ["Kubernetes"]
              예: "[주요업무]" 섹션의 "Local AD - Azure Entra ID 기반 SSO 관리" → requiredSkills: ["Active Directory", "Azure"]
            - 반드시 기술·도구·언어·프레임워크의 이름만 담아라. 단어 또는 짧은 고유명사여야 한다.
            - 요구사항을 설명하는 문장이나 서술을 절대 넣지 마라.
              예: "Python 또는 백엔드 개발 경험" (X) → "Python" (O)
                  (서술어인 "백엔드 개발 경험"은 버리고 기술명만 남긴다는 뜻이다. "또는"의 상대가 기술명이 아니므로 그룹이 아니라 requiredSkills에 "Python"만 넣는다.)
              예: "웹 서비스 및 RESTful API에 대한 기본 이해" (X) → "REST API" (O)
            - 방법론·개념·아키텍처 스타일 용어는 구체적인 기술/도구 이름이 아니므로 절대 포함하지 마라.
              절대 금지 목록(원문에 그대로 등장하더라도 출력에 포함하면 안 됨): MDD, MSA, Microservices, 마이크로서비스, 모놀리식, Monolithic, DDD, 도메인 주도 설계, 이벤트 기반 아키텍처, Event-Driven, TDD, BDD, 애자일, Agile, 스크럼, Scrum, 워터폴, CI/CD, DevOps,
              비동기 처리, 큐 기반 아키텍처, RDBMS, 관계형 데이터베이스, 객체지향
              (같은 개념의 한글/영문 표기, 축약어/풀네임 모두 동일하게 제외 대상이다. 프로그래밍 언어, 프레임워크, 라이브러리, 데이터베이스, 클라우드 서비스, 자격증, 협업 툴이 아니라면 넣지 마라)
              금지어와 기술명이 한 문구에 함께 있으면 금지어만 빼고 기술명은 남겨라.
              예: "SQL 및 관계형 데이터베이스 기본 이해" → "SQL" (관계형 데이터베이스는 개념어라 빼고, SQL은 기술명이라 남긴다)
            - 여러 기술을 묶어 지칭하는 카테고리 표현도 절대 포함하지 마라. (예: AI 개발도구, 협업 도구, 클라우드) 카테고리가 아니라 그 안에 언급된 구체적인 제품명만 추출하라.
              예: "AI 개발도구" (X) → 제외, "GitHub Copilot" (O) → 포함
            - 범용 프로토콜·데이터 포맷 이름은 절대 포함하지 마라: HTTP, JSON, XML (단, REST API는 실무에서 스킬로 통용되므로 예외적으로 유지한다)
            - 원문이 한글로 표기되어 있어도 기술명은 영문 표기로 통일하라. (예: "스프링 부트" → "Spring Boot", "자바" → "Java")
            - requiredSkills·requiredAnyOf·preferredSkills를 채운 뒤, 목록에 방법론/개념어/카테고리 표현/범용 프로토콜이 섞여 있지 않은지 다시 한번 스스로 검토하고 있다면 제거한 뒤 최종 JSON을 출력하라.
            - [주요업무] 섹션 문장 안에 "~로 전환", "~로 이전 예정", "향후 ~ 도입" 같은 로드맵/전환 계획을
              서술하는 문구가 있더라도, 그 문장에 언급된 기술이 [주요업무] 섹션에 있다면 requiredSkills로
              분류하라. 현재/미래 여부와 무관하게 섹션 위치 규칙을 우선한다.
            - 한 문장에 절대 금지 목록의 방법론/아키텍처 용어(MDD, MSA, 모놀리식 등)와
              실제 기술명(프로그래밍 언어, 프레임워크 등)이 함께 등장하는 경우, 방법론/아키텍처
              용어만 제외하고 기술명은 그대로 추출 대상에 포함하라.
              예: "MDD 기반 Java 모놀리식 시스템에서 Kotlin, Spring Boot 기반 MSA로 전환"
              → 추출 대상: Java, Kotlin, Spring Boot (MDD, 모놀리식, MSA는 제외)
              → 이 문장이 [주요업무] 섹션에 있다면 Java, Kotlin, Spring Boot는 requiredSkills로 분류
              [예시]
              입력 문장(주요업무 섹션): "MDD 기반 Java 모놀리식 코어뱅킹 시스템을 유지하면서,
              Kotlin + Spring Boot 기반 MSA 구조로 단계적으로 전환"
              → requiredSkills: ["Java", "Kotlin", "Spring Boot"] (MDD, 모놀리식, MSA는 제외)
              → 이유: 방법론/아키텍처 용어만 금지 목록이며, 전환 시점과 무관하게 [주요업무]
              섹션에 있는 기술명은 모두 requiredSkills로 분류한다.

            [requiredAnyOf 규칙]
            - requiredAnyOf는 섹션 위치 규칙상 필수(requiredSkills)로 분류될 기술 중, 그중 하나만 갖추면 되는 기술 묶음을 담는다.
              바깥 배열의 원소 하나가 그룹 하나이며, 그룹에는 기술명이 2개 이상 들어간다.
            - "A 또는 B", "A나 B", "A 혹은 B", "A/B 중 하나", "A, B 중 택1"처럼 대안 관계가 명시된 기술은 하나만 고르지 말고
              모두 같은 그룹에 넣어라. 그룹에 넣은 기술은 requiredSkills에 중복으로 넣지 마라.
              예: "[자격요건]" 섹션의 "Python 또는 Java 개발 경험" → requiredSkills: [], requiredAnyOf: [["Python", "Java"]]
              예: "[자격요건]" 섹션의 "Linux 또는 Windows 환경에서의 개발 및 기본 사용 능력" → requiredAnyOf: [["Linux", "Windows"]]
              예: "[자격요건]" 섹션의 "Java 개발 경험", "MySQL/PostgreSQL 중 하나 이상 사용 경험"
                  → requiredSkills: ["Java"], requiredAnyOf: [["MySQL", "PostgreSQL"]]
            - "TCP/IP", "CI/CD"처럼 슬래시가 한 기술명의 일부인 경우는 그룹이 아니다.
            - 섹션 위치 규칙상 preferredSkills로 분류되는 기술("~면 좋음", 우대사항, 기술 스택 소개 섹션 등)은 "또는"·"/"로 묶여 있어도
              requiredAnyOf에 넣지 말고 지금처럼 preferredSkills에 모두 넣어라. [자격요건] 섹션 안에 있더라도 문장이 "~면 좋음",
              "~이면 좋음", "~우대"로 끝나면 우대 표현이므로 그 문장의 기술은 requiredAnyOf가 아니라 preferredSkills다.
              예: "[자격요건]" 섹션의 "Java/Kotlin, Spring Framework 관련 개발 경험과 문제해결 능력이 있는 분이면 좋음"
                  → preferredSkills: ["Java", "Kotlin", "Spring Framework"], requiredAnyOf: []
            - 필수 요건이더라도 "어떤 스택이든 무관", "등 무관", "스택 무관", "언어 무관"처럼 특정 기술을 요구하지 않는다고 밝힌 나열은
              예시일 뿐이므로, 그 나열에 있는 기술은 requiredSkills·requiredAnyOf·preferredSkills 어디에도 넣지 마라.
              (같은 기술이 [기술 스택] 같은 다른 섹션에 따로 등장하면 그 섹션의 규칙대로 분류한다.)
              예: "[필수 요건]" 섹션의 "Django, Spring, Node, Next.js, React 등 어떤 스택이든 무관하나, 하나의 서비스를 끝까지 만들어본 경험"
                  → 이 문장에서는 아무 기술도 추출하지 않는다.
            - 해당하는 그룹이 없으면 빈 배열 []로 둬라.
            - requiredAnyOf의 기술에도 requiredSkills와 같은 기술명 규칙(개념어·카테고리 표현·범용 프로토콜 금지, 영문 표기 통일)을 적용하라.

            [requiredCertificates / preferredCertificates 규칙]
            - 이 규칙은 자격증·어학성적에만 적용된다. requiredSkills / preferredSkills 분류에는 영향을 주지 않는다.
            - 자격증·어학성적 요구는 원문 내 섹션 위치(우대사항, 필수 자격 등)와 무관하게, 문구 자체의 표현을 기준으로 필수 여부를 판단하라.
            - "필수", "제출 필수", "반드시"처럼 필수임을 나타내는 표현이 붙어 있다면, "우대사항" 섹션에 있더라도 requiredCertificates에 포함하라.
            - 그 외의 자격증·어학성적(필수 표현 없이 보유를 권장하는 수준, 주로 "우대사항"·"가점사항" 섹션)은 preferredCertificates에 포함하라.
              "우대 자격증: A, B, C"처럼 한 줄에 나열된 목록도 하나도 빠뜨리지 말고 모두 넣어라.
            - 자격증/어학성적의 명칭만 추출하고, 기간·점수 제출 방식 등 부가 설명은 제외하라.
              예: "[자격 및 우대사항]" 섹션의 "OPIC 또는 TOEIC Speaking 점수 제출 필수(2년 내)"
                  → requiredCertificates: ["OPIC", "TOEIC Speaking"]
              예: "[우대사항]" 섹션의 "정보처리기사, AWS 자격증 보유자"
                  → preferredCertificates: ["정보처리기사", "AWS"]
              예: "[가점사항]" 섹션의 "(자격증) 정보처리기사, 정보처리산업기사"
                  → preferredCertificates: ["정보처리기사", "정보처리산업기사"]
            - 어학성적 기준(TOEIC 850, OPIc IH 등)이 "또는 그에 준함", "가능자"처럼 필수 표현 없이 적혀 있으면,
              [자격요건] 섹션에 있더라도 preferredCertificates에 넣어라. 점수·등급은 빼고 시험명만 넣어라.
              예: "[자격요건]" 섹션의 "영어 가능자 (TOEIC 850, TOEIC SPEAKING/OPIC IH 이상 또는 그에 준함)"
                  → preferredCertificates: ["TOEIC", "TOEIC Speaking", "OPIc"]
            - "~에 상응하는", "~ 보유자"처럼 특정 시험명을 기준으로 든 어학성적 요구도 빠뜨리지 말고 시험명을 추출하라.
              필수 표현이 있으면 requiredCertificates, 없으면 preferredCertificates에 넣어라.
              예: "[자격요건]" 섹션의 "토익 700점에 상응하는 영어공인성적 보유자" → preferredCertificates: ["TOEIC"]

            [minYears / maxYears / newGraduateAllowed 규칙]
            - [상세 내용]의 자격요건에 "경력 N년 이상", "N년 이상의 ~ 경력", "N년 이상 ~ 경력자", "~ 경력 N년 이상"처럼
              경력 하한이 명시되면 minYears에 N을 넣고 newGraduateAllowed는 false로 둬라.
              [경력 조건]이 "신입/경력"이어도, [상세 내용]에 신입 지원을 허용하는 표현 없이 경력 하한만 있으면 newGraduateAllowed는 false다.
              예: [경력 조건] "신입/경력", [자격요건] "1년 이상의 서버 개발 경력" → minYears: 1, maxYears: null, newGraduateAllowed: false
            - "신입 또는 관련 경력 N년 이하", "신입~경력 N년 이하"처럼 신입과 경력 상한이 함께 명시되면 minYears는 null,
              maxYears에 N, newGraduateAllowed는 true로 둬라.
              예: "경력: 신입 또는 관련 경력 2년 이하" → minYears: null, maxYears: 2, newGraduateAllowed: true
            - "N년 이하"처럼 상한만 명시된 경우 maxYears에 N을 넣고 minYears는 null로 둬라.
            - "신입 또는 경력 N년 이상"처럼 신입 지원이 함께 명시되면 minYears에 N을 넣되 newGraduateAllowed는 true로 둬라.
            - 연수가 명시되지 않으면 minYears·maxYears를 null로 두고, newGraduateAllowed는 [경력 조건]이 "경력"뿐이면 false,
              "신입"·"무관"이 포함되어 있으면 true로 둬라.
            """;

    // SYSTEM_PROMPT나 EXTRACTION_SCHEMA를 바꾸면 올린다. 캐시 행의 버전과 다르면 재추출해 같은 행을 덮어쓴다.
    // 2: IT_GENERAL 추가, jobCategory null 허용, 카테고리별 사용 조건·가점사항 자격증 규칙 추가
    // 3: 회사 도메인이 아닌 직무명·주요업무 기준 판단 규칙 추가, AI_ML을 ML 자체가 주 업무일 때로 한정
    // 4: 주요업무의 "~ 기반", "~ 환경에서" 기술을 requiredSkills로, 필수 표현 없는 어학성적 기준을 preferredCertificates로 넣는 예시 추가
    // 5: [자격요건] 기술("또는"·"기본 이해" 포함)을 "~ 기반" 규칙과 별개로 유지하도록 명시, Azure Entra ID·"~에 상응하는" 어학 예시 추가
    // 6: requiredAnyOf(택1 필수 그룹) 추가, "어떤 스택이든 무관" 나열 제외, "N년 이상 ~ 경력" → minYears·newGraduateAllowed=false 규칙 추가
    static final int EXTRACTION_PROMPT_VERSION = 6;

    // LLM이 OTHER로 분류했더라도 공고 제목/본문에 이 키워드가 있으면 IT 직무일 가능성이 높아 OTHER를 신뢰하지 않는다.
    // "IT"는 영문 단어 일부(예: GitHub)와 겹치지 않도록 대소문자를 구분해 비교한다.
    private static final List<String> IT_KEYWORDS = List.of(
            "IT", "전산", "개발", "시스템", "정보보안", "인프라", "소프트웨어", "SW"
    );

    // 프롬프트의 스키마 설명만으로는 jobCategory 같은 필드가 응답에서 빠지는 경우가 있어 Structured Outputs로 강제한다.
    // 모델은 프로퍼티 선언 순서대로 값을 생성하므로, 프롬프트 스키마와 같은 순서를 유지하도록 LinkedHashMap을 쓴다.
    static final JsonSchemaFormat EXTRACTION_SCHEMA = new JsonSchemaFormat("job_requirement_extraction", extractionSchema());

    private static Map<String, Object> extractionSchema() {
        Map<String, Object> stringArray = Map.of("type", "array", "items", Map.of("type", "string"));
        Map<String, Object> nullableInteger = Map.of("type", List.of("integer", "null"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("requiredSkills", stringArray);
        // 그룹이 없는 공고가 대부분이므로 minItems를 두지 않아 빈 배열을 허용한다.
        properties.put("requiredAnyOf", Map.of("type", "array", "items", stringArray));
        properties.put("preferredSkills", stringArray);
        properties.put("minYears", nullableInteger);
        properties.put("maxYears", nullableInteger);
        properties.put("newGraduateAllowed", Map.of("type", "boolean"));
        properties.put("requiredCertificates", stringArray);
        properties.put("preferredCertificates", stringArray);
        // strict 모드에서 null을 허용하려면 type과 enum 양쪽에 null이 있어야 한다. List.of는 null을 받지 않아 ArrayList를 쓴다.
        List<String> jobCategoryValues = new ArrayList<>(Arrays.stream(JobCategory.values()).map(Enum::name).toList());
        jobCategoryValues.add(null);
        properties.put("jobCategory", Map.of(
                "type", List.of("string", "null"),
                "enum", jobCategoryValues
        ));

        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.copyOf(properties.keySet()),
                "additionalProperties", false
        );
    }

    // LLM이 구체적 기술명이 아닌 개념어/서술어를 requiredSkills/preferredSkills에 섞어 넣는 경우가 있어
    // 프롬프트 규칙만으로 막히지 않는 잔여 케이스를 후처리로 한 번 더 제거한다.
    // "Http", "cors"처럼 표기만 다른 경우도 걸러지도록 소문자로 정규화해 비교한다.
    private static final Set<String> CONCEPT_SKILL_BLOCKLIST = Stream.of(
            "비동기 프로그래밍",
            "동시성 프로그래밍",
            "데이터 시각화",
            "네트워크 통신 프로그래밍",
            "웹 보안",
            "LLM",
            "AI",
            "생성형 AI",
            // HTTP는 프롬프트 금지어(범용 프로토콜)이고, SSL·XSS·CORS는 "웹 보안"과 같은 성격의 웹 보안 개념이다.
            "HTTP",
            "SSL",
            "XSS",
            "CORS",
            // CI/CD는 프롬프트 금지어(방법론)인데도 우대 기술 목록에 섞여 다시 추출되는 경우가 있었다. (원테크스탑 55205788)
            "CI/CD"
    ).map(JobRequirementExtractor::normalizeForBlocklist).collect(Collectors.toUnmodifiableSet());

    private final LlmClient llmClient;
    private final JobRequirementParser jobRequirementParser;
    private final ObjectMapper objectMapper;
    private final JobRequirementExtractionRepository jobRequirementExtractionRepository;

    // 외부(EvaluationQueryService 등)의 readOnly 트랜잭션 안에서 호출되더라도 캐시 저장(INSERT)이
    // 항상 성공하도록 별도의 쓰기 가능한 트랜잭션에서 실행한다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobRequirementExtraction extract(JobPosting jobPosting) {
        return extract(jobPosting, new LlmCallBreaker());
    }

    // 1) 현재 버전 캐시가 있으면 사용한다.
    // 2) 옛 버전 캐시만 있으면 재추출을 시도하고, 실패하면 옛 캐시를 덮어쓰지 않고 그대로 쓴다.
    //    LLM 장애 중에 정규식 fallback(기술·카테고리 없음)으로 떨어지는 것보다 옛 추출 결과가 훨씬 정확하기 때문이다.
    // 3) 캐시가 없으면 추출을 시도하고, 실패하면 정규식 fallback을 쓰되 캐시하지 않는다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobRequirementExtraction extract(JobPosting jobPosting, LlmCallBreaker breaker) {
        Optional<JobRequirementExtractionEntity> cached = jobRequirementExtractionRepository.findByJobPostingId(jobPosting.getId());
        if (cached.isPresent() && cached.get().isExtractedWith(EXTRACTION_PROMPT_VERSION)) {
            // 블록리스트 추가 이전에 캐시된 결과에도 동일한 필터가 적용되도록, 조회 시에도 다시 거른다.
            return removeConceptSkills(cached.get().toExtraction());
        }

        JobRequirementExtractionEntity staleEntity = cached.orElse(null);
        Optional<JobRequirementExtraction> extracted = tryRequestExtraction(jobPosting, breaker);
        if (extracted.isPresent()) {
            saveToCache(jobPosting, staleEntity, extracted.get());
            return extracted.get();
        }
        if (staleEntity != null) {
            log.warn("재추출 실패로 옛 버전 캐시 사용: jobPostingId={}, cachedPromptVersion={}, currentPromptVersion={}",
                    jobPosting.getId(), staleEntity.getPromptVersion(), EXTRACTION_PROMPT_VERSION);
            return removeConceptSkills(staleEntity.toExtraction());
        }
        // fallback 결과는 저장하지 않는다. LLM 장애가 해소되면 다음 평가에서 다시 시도하도록 하기 위함.
        return fallback(jobPosting);
    }

    // 캐시와 프롬프트 버전에 관계없이 다시 추출해 캐시 행을 덮어쓴다. 특정 공고의 추출 결과를 점검·교정할 때 쓴다.
    // 평가 경로와 달리 LLM 실패 시 정규식 fallback으로 대체하지 않는다. fallback 결과를 "재추출 결과"로 돌려주면 오해를 낳기 때문이다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public JobRequirementReExtraction reExtract(JobPosting jobPosting) {
        Optional<JobRequirementExtractionEntity> cached = jobRequirementExtractionRepository.findByJobPostingId(jobPosting.getId());
        // overwrite가 엔티티를 변경하기 전에 변경 전 값을 복사해 둔다.
        JobRequirementExtraction before = cached.map(JobRequirementExtractionEntity::toExtraction).orElse(null);

        JobRequirementExtraction after;
        try {
            after = requestExtraction(jobPosting);
        } catch (RuntimeException e) {
            throw new LlmResponseException("요구사항 재추출에 실패했습니다. jobPostingId=%d, 원인=%s"
                    .formatted(jobPosting.getId(), e.getMessage()));
        }
        saveToCache(jobPosting, cached.orElse(null), after);
        return new JobRequirementReExtraction(before, after);
    }

    // 연속 실패로 breaker가 열려 있으면 LLM을 호출하지 않고 실패로 처리한다.
    private Optional<JobRequirementExtraction> tryRequestExtraction(JobPosting jobPosting, LlmCallBreaker breaker) {
        if (breaker.isOpen()) {
            log.warn("LLM 추출이 연속 실패해 호출을 건너뜀: jobPostingId={}", jobPosting.getId());
            return Optional.empty();
        }
        try {
            JobRequirementExtraction extraction = requestExtraction(jobPosting);
            breaker.recordSuccess();
            return Optional.of(extraction);
        } catch (RuntimeException e) {
            breaker.recordFailure();
            log.warn("LLM 추출 실패: jobPostingId={}, 원인={}", jobPosting.getId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    private JobRequirementExtraction requestExtraction(JobPosting jobPosting) {
        String response = llmClient.complete(SYSTEM_PROMPT, buildUserPrompt(jobPosting), EXTRACTION_SCHEMA);
        return distrustOtherForItPosting(jobPosting,
                removeConceptSkills(objectMapper.readValue(response, JobRequirementExtraction.class)));
    }

    // 기술이 비어 있거나 jobCategory가 null인 결과도 캐시한다. 캐시하지 않으면 평가마다 LLM을 다시 불러
    // 결과가 흔들리다가 우연히 나온 값이 고정되는 문제가 있었다. 잘못된 캐시는 프롬프트 버전을 올려 재추출한다.
    // 기존 행이 있으면 job_posting_id 유니크 제약과 충돌하지 않도록 새로 INSERT하지 않고 같은 행을 덮어쓴다.
    private void saveToCache(JobPosting jobPosting, JobRequirementExtractionEntity existingEntity,
                             JobRequirementExtraction extraction) {
        if (existingEntity == null) {
            jobRequirementExtractionRepository.save(
                    new JobRequirementExtractionEntity(jobPosting, extraction, EXTRACTION_PROMPT_VERSION));
            return;
        }
        existingEntity.overwrite(extraction, EXTRACTION_PROMPT_VERSION);
        jobRequirementExtractionRepository.save(existingEntity);
    }

    private String buildUserPrompt(JobPosting jobPosting) {
        return """
                [공고 제목]
                %s

                [경력 조건]
                %s

                [등록된 키워드]
                %s
                
                [상세 내용]
                %s
                """.formatted(
                nullToEmpty(jobPosting.getTitle()),
                nullToEmpty(jobPosting.getExperienceLevel()),
                nullToEmpty(jobPosting.getKeywords()),
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
                // 판정 불가(UNKNOWN)는 본문 근거가 아니므로 기존 fallback처럼 false로 두고, 신입 가능으로 분류된 경우만 true로 둔다.
                requirement.experienceLevelType() == ExperienceLevelType.NEW_GRADUATE_ALLOWED,
                List.of(),
                List.of(),
                null
        );
    }

    // OTHER는 곧바로 UNFIT으로 이어지고 캐시에 고정되므로, IT 공고로 볼 근거가 있으면 판정 불가(null)로 낮춰 통과시킨다.
    private JobRequirementExtraction distrustOtherForItPosting(JobPosting jobPosting, JobRequirementExtraction extraction) {
        if (extraction.jobCategory() != JobCategory.OTHER) {
            return extraction;
        }
        boolean hasSkills = extraction.hasRequiredSkills() || !extraction.preferredSkills().isEmpty();
        if (!hasSkills && !containsItKeyword(jobPosting)) {
            return extraction;
        }
        log.info("IT 공고로 보이는데 OTHER로 추출되어 jobCategory를 null로 변경: jobPostingId={}, original={}, hasSkills={}",
                jobPosting.getId(), extraction.jobCategory(), hasSkills);
        return withJobCategory(extraction, null);
    }

    private boolean containsItKeyword(JobPosting jobPosting) {
        String text = nullToEmpty(jobPosting.getTitle()) + "\n" + nullToEmpty(jobPosting.getDescription());
        return IT_KEYWORDS.stream().anyMatch(text::contains);
    }

    private JobRequirementExtraction withJobCategory(JobRequirementExtraction extraction, JobCategory jobCategory) {
        return new JobRequirementExtraction(
                extraction.requiredSkills(),
                extraction.requiredAnyOf(),
                extraction.preferredSkills(),
                extraction.minYears(),
                extraction.maxYears(),
                extraction.newGraduateAllowed(),
                extraction.requiredCertificates(),
                extraction.preferredCertificates(),
                jobCategory
        );
    }

    // 택1 그룹 안의 기술에도 같은 개념어 필터를 적용한다. 필터 후 기술이 하나만 남은 그룹은 사실상 단일 필수 기술이므로
    // requiredSkills로 옮기고, 하나도 남지 않은 그룹은 버린다. 그룹에 있는 기술이 requiredSkills에도 중복으로 들어오면
    // 그 기술만 따로 필수로 계산돼 택1 의미가 사라지므로 requiredSkills에서 뺀다.
    private JobRequirementExtraction removeConceptSkills(JobRequirementExtraction extraction) {
        List<List<String>> anyOfGroups = new ArrayList<>();
        List<String> promotedSkills = new ArrayList<>();
        for (List<String> group : extraction.requiredAnyOf()) {
            List<String> filtered = filterConceptSkills(group).stream().distinct().toList();
            if (filtered.size() >= 2) {
                anyOfGroups.add(filtered);
            } else if (filtered.size() == 1) {
                promotedSkills.add(filtered.getFirst());
            }
        }
        Set<String> groupedSkills = anyOfGroups.stream()
                .flatMap(List::stream)
                .map(JobRequirementExtractor::normalizeForBlocklist)
                .collect(Collectors.toSet());
        List<String> requiredSkills = Stream.concat(
                        filterConceptSkills(extraction.requiredSkills()).stream(), promotedSkills.stream())
                .filter(skill -> !groupedSkills.contains(normalizeForBlocklist(skill)))
                .distinct()
                .toList();

        return new JobRequirementExtraction(
                requiredSkills,
                List.copyOf(anyOfGroups),
                filterConceptSkills(extraction.preferredSkills()),
                extraction.minYears(),
                extraction.maxYears(),
                extraction.newGraduateAllowed(),
                extraction.requiredCertificates(),
                extraction.preferredCertificates(),
                extraction.jobCategory()
        );
    }

    private List<String> filterConceptSkills(List<String> skills) {
        return skills.stream()
                .filter(skill -> !CONCEPT_SKILL_BLOCKLIST.contains(normalizeForBlocklist(skill)))
                .toList();
    }

    private static String normalizeForBlocklist(String skill) {
        return skill.trim().toLowerCase(Locale.ROOT);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
