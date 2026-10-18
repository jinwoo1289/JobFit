package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ScoreCalculatorTest {

    private final ScoreCalculator scoreCalculator = new ScoreCalculator(
            new SkillScoreCalculator(new SkillAliasNormalizer()), new ExperienceScoreCalculator(), new CertificateScoreCalculator());

    @Test
    void calculate_tossBank_combinesAllFourScores() {
        UserProfile profile = profile();
        List<UserSkill> skills = List.of(
                new UserSkill(profile, "Java"),
                new UserSkill(profile, "Kotlin"),
                new UserSkill(profile, "Spring Boot"),
                new UserSkill(profile, "AWS"),
                new UserSkill(profile, "Redis")
        );
        List<UserProject> projects = List.of(
                new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스", List.of("Java", "Spring Boot", "JPA", "Redis", "AWS")),
                new UserProject(profile, "KakaoMCP", "MCP 연동 프로젝트", List.of("Java", "Spring Boot", "Docker"))
        );
        List<UserCertificate> certificates = List.of(
                new UserCertificate(profile, "OPIC", "IH", null)
        );
        JobRequirementExtraction extraction = new JobRequirementExtraction(
                List.of("Java", "Kotlin", "Spring Boot"),
                List.of("AWS", "Redis"),
                null, null, true,
                List.of("OPIC"),
                List.of(),
                JobCategory.BACKEND_DEVELOPMENT
        );

        ScoreResult result = scoreCalculator.calculate(skills, projects, certificates, extraction);

        assertThat(result.skillScore()).isCloseTo(260.0 / 3, within(0.001));
        assertThat(result.experienceScore()).isCloseTo(200.0 / 3, within(0.001));
        assertThat(result.preferenceScore()).isCloseTo(100.0, within(0.001));
        assertThat(result.certificateScore()).isCloseTo(100.0, within(0.001));
    }

    // 파수에이아이(55234023)는 [주요 기술]에 우대 기술 구분이 없어 preferredSkills가 비어 있고, 자격증
    // 요구도 전혀 없는 경우를 가정. preference(20)와 certificate(10) 가중치가 제외되어 skill(40)/experience(30)에
    // 비례 배분된다. 두 값의 비율이 동일하게 배분되므로 결과적으로 skillScore(=experienceScore)와 같아진다.
    @Test
    void calculateTotalScore_pasuAi_emptyPreferredSkillsAndCertificates_redistributesToSkillAndExperience() {
        UserProfile profile = profile();
        List<UserSkill> skills = skills(profile);
        List<UserProject> projects = projects(profile);
        JobRequirementExtraction extraction = new JobRequirementExtraction(
                List.of("Java", "Spring Boot", "RDBMS/SQL", "React.js", "Redux", "TypeScript"),
                List.of(),
                null, null, true,
                List.of(),
                List.of(),
                JobCategory.BACKEND_DEVELOPMENT
        );
        EvaluationWeight weight = new EvaluationWeight(profile, 40, 30, 20, 10);

        ScoreResult scoreResult = scoreCalculator.calculate(skills, projects, List.of(), extraction);
        double totalScore = scoreCalculator.calculateTotalScore(scoreResult, weight);

        assertThat(scoreResult.preferenceExcluded()).isTrue();
        assertThat(scoreResult.certificateExcluded()).isTrue();
        assertThat(totalScore).isCloseTo(100.0 / 3, within(0.001));
    }

    // 원테크스탑(55205788)은 [자격요건]에 기술 요구가 없고 [우대사항]에만 핵심 기술 스택이 있으며, 자격증
    // 요구도 전혀 없는 경우를 가정. skill(40)/experience(30)/certificate(10) 가중치가 모두 제외되어
    // preference(20)만 남아, 총점은 preferenceScore 그대로(가중치 100%)가 된다.
    @Test
    void calculateTotalScore_wonTechStop_emptyRequiredSkillsAndCertificates_leavesOnlyPreferenceActive() {
        UserProfile profile = profile();
        List<UserSkill> skills = skills(profile);
        List<UserProject> projects = projects(profile);
        JobRequirementExtraction extraction = new JobRequirementExtraction(
                List.of(),
                List.of("Java", "Python", "Node.js", "NestJS", "Next.js", "React", "JSP", "PostgreSQL", "Oracle", "GCP", "Git", "CI/CD"),
                null, null, true,
                List.of(),
                List.of(),
                JobCategory.FULLSTACK_DEVELOPMENT
        );
        EvaluationWeight weight = new EvaluationWeight(profile, 40, 30, 20, 10);

        ScoreResult scoreResult = scoreCalculator.calculate(skills, projects, List.of(), extraction);
        double totalScore = scoreCalculator.calculateTotalScore(scoreResult, weight);

        assertThat(scoreResult.skillExcluded()).isTrue();
        assertThat(scoreResult.experienceExcluded()).isTrue();
        assertThat(scoreResult.certificateExcluded()).isTrue();
        assertThat(totalScore).isCloseTo(25.0 / 3, within(0.001));
    }

    // CJ ENM(55101818)은 [주요업무]/[자격요건]에 구체적 기술명이 없고 [우대사항]에만 "Java"가 언급되며,
    // 자격증 요건은 전혀 없는 경우를 가정. requiredSkills가 비어 skill/experience가 모두 제외되고,
    // 필수·우대 자격증이 모두 비어 certificate도 제외되어, 활성 항목은 preference(20)만 남는다.
    @Test
    void calculateTotalScore_cjEnm_onlyPreferredSkillPresent_excludesSkillExperienceAndCertificate() {
        UserProfile profile = profile();
        List<UserSkill> skills = skills(profile);
        List<UserProject> projects = projects(profile);
        JobRequirementExtraction extraction = new JobRequirementExtraction(
                List.of(),
                List.of("Java"),
                null, null, true,
                List.of(),
                List.of(),
                JobCategory.BACKEND_DEVELOPMENT
        );
        EvaluationWeight weight = new EvaluationWeight(profile, 40, 30, 20, 10);

        ScoreResult scoreResult = scoreCalculator.calculate(skills, projects, List.of(), extraction);
        double totalScore = scoreCalculator.calculateTotalScore(scoreResult, weight);

        assertThat(scoreResult.skillExcluded()).isTrue();
        assertThat(scoreResult.experienceExcluded()).isTrue();
        assertThat(scoreResult.certificateExcluded()).isTrue();
        assertThat(scoreResult.preferenceExcluded()).isFalse();
        // Java는 skills()/projects() 모두에 존재하므로 완전 일치(100점), preference만 활성 상태라 총점도 그대로 100.
        assertThat(totalScore).isCloseTo(100.0, within(0.001));
    }

    // 모든 항목이 평가 제외되면(= 비교 가능한 정보가 전혀 없으면) 재분배할 가중치 자체가 없어 0점을 반환해야 한다.
    @Test
    void calculateTotalScore_allExcluded_returnsZero() {
        UserProfile profile = profile();
        JobRequirementExtraction extraction = new JobRequirementExtraction(
                List.of(), List.of(), null, null, true, List.of(), List.of(), JobCategory.OTHER
        );
        EvaluationWeight weight = new EvaluationWeight(profile, 40, 30, 20, 10);

        ScoreResult scoreResult = scoreCalculator.calculate(List.of(), List.of(), List.of(), extraction);
        double totalScore = scoreCalculator.calculateTotalScore(scoreResult, weight);

        assertThat(scoreResult.allExcluded()).isTrue();
        assertThat(totalScore).isEqualTo(0.0);
    }

    private List<UserSkill> skills(UserProfile profile) {
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

    private List<UserProject> projects(UserProfile profile) {
        return List.of(
                new UserProject(profile, "HopeTail-BE", "채용 매칭 서비스", List.of("Java", "Spring Boot", "JPA", "Redis", "AWS")),
                new UserProject(profile, "KakaoMCP", "MCP 연동 프로젝트", List.of("Java", "Spring Boot", "Docker"))
        );
    }

    private UserProfile profile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }
}
