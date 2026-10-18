package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.evaluation.vo.SkillScore;
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
        SkillScore skill = skillScoreCalculator.calculate(skills, projects, extraction.requiredSkills());
        SkillScore preference = skillScoreCalculator.calculate(skills, projects, extraction.preferredSkills());
        double experienceScore = experienceScoreCalculator.calculate(projects, extraction.requiredSkills());
        double certificateScore = certificateScoreCalculator.calculate(
                certificates, extraction.requiredCertificates(), extraction.preferredCertificates());

        // 경험 점수는 requiredSkills를 기준으로 계산하므로 skill과 동일한 조건으로 평가 제외된다.
        boolean experienceExcluded = extraction.requiredSkills().isEmpty();
        // 필수·우대 자격증이 모두 비어 있으면 비교할 자격증 요건 자체가 없는 것이므로 평가 제외한다.
        boolean certificateExcluded = extraction.requiredCertificates().isEmpty() && extraction.preferredCertificates().isEmpty();

        return new ScoreResult(
                skill.value(), skill.excluded(),
                experienceScore, experienceExcluded,
                preference.value(), preference.excluded(),
                certificateScore, certificateExcluded
        );
    }

    // 평가 제외된 항목의 가중치를, 나머지 평가 대상 항목에 비율을 유지한 채로 재분배한다
    // (예: 우대 기술이 없으면 preferenceWeight를 skill/experience/certificate에 배분).
    public double calculateTotalScore(ScoreResult scoreResult, EvaluationWeight evaluationWeight) {
        double skillWeight = scoreResult.skillExcluded() ? 0 : evaluationWeight.getSkillWeight();
        double experienceWeight = scoreResult.experienceExcluded() ? 0 : evaluationWeight.getExperienceWeight();
        double preferenceWeight = scoreResult.preferenceExcluded() ? 0 : evaluationWeight.getPreferenceWeight();
        double certificateWeight = scoreResult.certificateExcluded() ? 0 : evaluationWeight.getCertificateWeight();

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
