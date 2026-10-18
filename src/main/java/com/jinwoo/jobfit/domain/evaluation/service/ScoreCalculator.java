package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScoreCalculator {

    private static final double TOTAL_WEIGHT = 100;

    private final SkillScoreCalculator skillScoreCalculator;
    private final ExperienceScoreCalculator experienceScoreCalculator;
    private final CertificateScoreCalculator certificateScoreCalculator;

    public ScoreResult calculate(List<UserSkill> skills,
                                  List<UserProject> projects,
                                  List<UserCertificate> certificates,
                                  JobRequirementExtraction extraction) {
        double skillScore = skillScoreCalculator.calculate(skills, projects, extraction.requiredSkills()).value();
        double preferenceScore = skillScoreCalculator.calculate(skills, projects, extraction.preferredSkills()).value();
        double experienceScore = experienceScoreCalculator.calculate(projects, extraction.requiredSkills());
        double certificateScore = certificateScoreCalculator.calculate(
                certificates, extraction.requiredCertificates(), extraction.preferredCertificates());
        return new ScoreResult(skillScore, experienceScore, preferenceScore, certificateScore);
    }

    // 필수/우대 기술이 공고에 명시되지 않아 평가 제외된 항목의 가중치를, 나머지 평가 대상 항목에
    // 비율을 유지한 채로 재분배한다 (예: 우대 기술이 없으면 preferenceWeight를 skill/experience/certificate에 배분).
    public double calculateTotalScore(ScoreResult scoreResult, JobRequirementExtraction extraction, EvaluationWeight evaluationWeight) {
        boolean skillExcluded = extraction.requiredSkills().isEmpty();
        boolean preferenceExcluded = extraction.preferredSkills().isEmpty();

        double skillWeight = skillExcluded ? 0 : evaluationWeight.getSkillWeight();
        double experienceWeight = evaluationWeight.getExperienceWeight();
        double preferenceWeight = preferenceExcluded ? 0 : evaluationWeight.getPreferenceWeight();
        double certificateWeight = evaluationWeight.getCertificateWeight();

        double activeWeightSum = skillWeight + experienceWeight + preferenceWeight + certificateWeight;
        if (activeWeightSum == 0) {
            return 0;
        }
        double scale = TOTAL_WEIGHT / activeWeightSum;

        return (scoreResult.skillScore() * skillWeight * scale
                + scoreResult.experienceScore() * experienceWeight * scale
                + scoreResult.preferenceScore() * preferenceWeight * scale
                + scoreResult.certificateScore() * certificateWeight * scale) / TOTAL_WEIGHT;
    }
}
