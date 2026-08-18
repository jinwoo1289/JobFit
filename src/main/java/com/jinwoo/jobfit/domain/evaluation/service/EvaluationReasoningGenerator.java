package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.client.LlmClient;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvaluationReasoningGenerator {

    private static final String SYSTEM_PROMPT = """
            너는 채용 공고와 지원자 프로필을 비교해 적합도 판단 근거를 작성하는 어시스턴트다.
            반드시 아래 스키마를 따르는 JSON만 출력하라. 마크다운 코드블록이나 설명 문구는 절대 출력하지 마라.

            {
              "matchedPoints": string[],
              "gapPoints": string[],
              "checkPoints": string[]
            }

            [작성 규칙]
            - 공고 원문(제목, 상세내용)과 추출된 요구사항에 없는 내용을 절대 지어내지 마라.
            - 근거 문장에는 [지원자 보유 기술 목록], [지원자 프로젝트], [지원자 보유 자격증], [지원자 희망 조건]에
              명시된 내용만 사용하라. 구체적인 배포판·버전·도구명 등 프로필에 없는 세부 정보를 추가하지 마라.
              예: 프로필에 "Linux"만 있으면 "Ubuntu 22.04"처럼 구체적인 배포판명을 지어내지 마라.
              예: 프로필에 "Docker"만 있으면 "Docker 24.0"처럼 구체적인 버전을 지어내지 마라.
            - matchedPoints, gapPoints의 각 항목은 "공고 요구 → 사용자 근거" 형태로 연결해서 작성하라.
              예: "Spring Boot 충족 — HopeTail-BE에서 REST API 및 인증 기능 구현"
            - matchedPoints: 공고가 요구하는 조건 중 지원자가 충족하는 항목.
            - gapPoints: 공고가 요구하는 조건 중 지원자가 충족하지 못하는 항목.
            - checkPoints: 공고 원문이나 추출 정보만으로는 충족 여부를 단정하기 어려워 지원자가 직접 확인해야 하는 항목.
            - 근거가 없는 항목은 만들어내지 말고 빈 배열로 두어라.

            [matchedPoints 판정 규칙 — 반드시 지켜라]
            - 사용자 프롬프트 맨 앞의 [지원자 보유 기술 목록]을 항상 먼저 확인하라.
            - 보유 기술 목록에 있고 [지원자 프로젝트]에서 실제로 사용한 기술은 반드시 matchedPoints에 포함하라. 누락하지 마라.
            - 보유 기술 목록에 없는 기술은 절대 matchedPoints에 넣지 마라.
            - 유사하거나 대체 가능하다는 이유로 충족으로 판정하지 마라.
              예: Git 경험을 Jira/Confluence 충족 근거로 쓰지 마라.
              예: Java 경험을 Python 충족 근거로 쓰지 마라.
            - matchedPoints 각 항목에는 근거가 된 프로젝트명을 포함해서 작성하라. 단, 해당 기술을 사용한 프로젝트가 없다면
              프로젝트명을 지어내지 말고 "보유 기술 목록에 포함" 정도로만 서술하라.
            - 추출된 요구사항의 필수 기술(requiredSkills)이 비어 있으면, 판정할 기술 요구 자체가 없는 것이므로
              matchedPoints는 반드시 빈 배열로 출력하라.
            - requiredCertificates(필수 자격증)는 "또는" 관계로 해석하라. 여러 개 중 하나라도 보유하면 그 요구는 충족된 것으로 판단하라.

            [gapPoints 판정 규칙 — 반드시 지켜라]
            - gapPoints에는 추출된 요구사항의 필수 기술(requiredSkills) 중 [지원자 보유 기술 목록]에 없는 기술만 넣어라.
            - 공고가 요구하지 않은 기술, 즉 requiredSkills에 없는 기술은 gapPoints에 절대 언급하지 마라.
            - gapPoints 항목에는 "충족"이라는 표현을 쓰지 마라. "미보유", "부족" 등 결핍을 나타내는 표현만 사용하라.
            """;

    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public EvaluationReasoning generate(JobPosting jobPosting,
                                         JobRequirementExtraction extraction,
                                         UserProfile userProfile,
                                         List<UserSkill> skills,
                                         List<UserProject> projects,
                                         List<UserCertificate> certificates,
                                         ScoreResult scoreResult) {
        try {
            String response = llmClient.complete(SYSTEM_PROMPT,
                    buildUserPrompt(jobPosting, extraction, userProfile, skills, projects, certificates, scoreResult));
            return objectMapper.readValue(response, EvaluationReasoning.class);
        } catch (RuntimeException e) {
            log.warn("LLM 근거 생성 실패, 규칙 기반 fallback 사용: {}", e.getMessage(), e);
            return fallback(skills, projects, extraction);
        }
    }

    private String buildUserPrompt(JobPosting jobPosting,
                                    JobRequirementExtraction extraction,
                                    UserProfile userProfile,
                                    List<UserSkill> skills,
                                    List<UserProject> projects,
                                    List<UserCertificate> certificates,
                                    ScoreResult scoreResult) {
        return """
                [지원자 보유 기술 목록]
                %s
                ※ 이 목록에 없는 기술은 절대 matchedPoints에 넣지 마라.

                [공고 제목]
                %s

                [공고 상세 내용]
                %s

                [추출된 요구사항]
                - 필수 기술: %s
                - 우대 기술: %s
                - 요구 경력: %s ~ %s 년 (신입 지원 가능: %s)
                - 필수 자격증: %s

                [지원자 희망 조건]
                - 희망 직무: %s
                - 경력 수준: %s (연차: %s)

                [지원자 프로젝트]
                %s

                [지원자 보유 자격증]
                %s

                [산출된 점수]
                - 스킬 점수: %.1f
                - 경력 점수: %.1f
                - 우대사항 점수: %.1f
                - 자격증 점수: %.1f
                """.formatted(
                describeSkills(skills),
                nullToEmpty(jobPosting.getTitle()),
                nullToEmpty(jobPosting.getDescription()),
                joinOrNone(extraction.requiredSkills()),
                joinOrNone(extraction.preferredSkills()),
                nullToText(extraction.minYears()), nullToText(extraction.maxYears()), extraction.newGraduateAllowed(),
                joinOrNone(extraction.requiredCertificates()),
                nullToEmpty(userProfile.getDesiredJob()),
                userProfile.getCareerLevel(), nullToText(userProfile.getYearsOfExperience()),
                describeProjects(projects),
                describeCertificates(certificates),
                scoreResult.skillScore(), scoreResult.experienceScore(),
                scoreResult.preferenceScore(), scoreResult.certificateScore()
        );
    }

    private String describeSkills(List<UserSkill> skills) {
        if (skills.isEmpty()) {
            return "없음";
        }
        return skills.stream().map(UserSkill::getSkillName).collect(Collectors.joining(", "));
    }

    private String describeProjects(List<UserProject> projects) {
        if (projects.isEmpty()) {
            return "없음";
        }
        return projects.stream()
                .map(project -> "- %s (%s): %s".formatted(
                        project.getProjectName(),
                        String.join(", ", project.getTechStack()),
                        nullToEmpty(project.getDescription())
                ))
                .collect(Collectors.joining("\n"));
    }

    private String describeCertificates(List<UserCertificate> certificates) {
        if (certificates.isEmpty()) {
            return "없음";
        }
        return certificates.stream().map(UserCertificate::getCertificateName).collect(Collectors.joining(", "));
    }

    private EvaluationReasoning fallback(List<UserSkill> skills, List<UserProject> projects, JobRequirementExtraction extraction) {
        Set<String> userSkillNames = skills.stream()
                .map(UserSkill::getSkillName)
                .map(this::normalize)
                .collect(Collectors.toSet());

        List<String> matchedPoints = new ArrayList<>();
        List<String> gapPoints = new ArrayList<>();

        for (String requiredSkill : extraction.requiredSkills()) {
            String normalized = normalize(requiredSkill);
            if (!userSkillNames.contains(normalized)) {
                gapPoints.add(requiredSkill + " 미보유");
                continue;
            }

            List<String> projectNames = projects.stream()
                    .filter(project -> project.getTechStack().contains(normalized))
                    .map(UserProject::getProjectName)
                    .toList();
            matchedPoints.add(projectNames.isEmpty()
                    ? requiredSkill + " 충족"
                    : requiredSkill + " 충족 — " + String.join(", ", projectNames) + "에서 사용");
        }

        return new EvaluationReasoning(matchedPoints, gapPoints, List.of());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String joinOrNone(List<String> values) {
        return values.isEmpty() ? "없음" : String.join(", ", values);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String nullToText(Integer value) {
        return value == null ? "제한없음" : value.toString();
    }
}
