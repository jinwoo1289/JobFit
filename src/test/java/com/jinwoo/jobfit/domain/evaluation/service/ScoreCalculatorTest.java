package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobCategory;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.user.entity.CareerLevel;
import com.jinwoo.jobfit.domain.user.entity.EmploymentType;
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
            new SkillScoreCalculator(), new ExperienceScoreCalculator(), new CertificateScoreCalculator());

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
                JobCategory.BACKEND_DEVELOPMENT
        );

        ScoreResult result = scoreCalculator.calculate(skills, projects, certificates, extraction);

        assertThat(result.skillScore()).isCloseTo(260.0 / 3, within(0.001));
        assertThat(result.experienceScore()).isCloseTo(200.0 / 3, within(0.001));
        assertThat(result.preferenceScore()).isCloseTo(100.0, within(0.001));
        assertThat(result.certificateScore()).isCloseTo(100.0, within(0.001));
    }

    private UserProfile profile() {
        return new UserProfile("백엔드 개발자", CareerLevel.NEW_GRADUATE, null, "서울", EmploymentType.FULL_TIME);
    }
}
