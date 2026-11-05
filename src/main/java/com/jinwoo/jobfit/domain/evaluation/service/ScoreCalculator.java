package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.RequiredSkillMatch;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.evaluation.vo.SkillMatchResult;
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
        // 택1 그룹(requiredAnyOf)은 그룹 하나를 필수 기술 1개로 계산한다.
        List<List<String>> requiredSkillGroups = extraction.requiredSkillGroups();
        SkillScore skill = skillScoreCalculator.calculateForGroups(skills, projects, requiredSkillGroups);
        SkillScore preference = skillScoreCalculator.calculate(skills, projects, extraction.preferredSkills());
        double experienceScore = experienceScoreCalculator.calculateForGroups(projects, requiredSkillGroups);
        double certificateScore = certificateScoreCalculator.calculate(
                certificates, extraction.requiredCertificates(), extraction.preferredCertificates());

        // 경험 점수는 필수 기술(택1 그룹 포함)을 기준으로 계산하므로 skill과 동일한 조건으로 평가 제외된다.
        boolean experienceExcluded = !extraction.hasRequiredSkills();
        // 필수·우대 자격증이 모두 비어 있으면 비교할 자격증 요건 자체가 없는 것이므로 평가 제외한다.
        boolean certificateExcluded = extraction.requiredCertificates().isEmpty() && extraction.preferredCertificates().isEmpty();

        return new ScoreResult(
                skill.value(), skill.excluded(),
                experienceScore, experienceExcluded,
                preference.value(), preference.excluded(),
                certificateScore, certificateExcluded
        );
    }

    // 점수 계산과 같은 기준으로 필수 기술 매칭 개수와 일치 기술별 사용 프로젝트를 요약한다. LLM을 호출하지 않는다.
    public RequiredSkillMatch requiredSkillMatch(List<UserSkill> skills,
                                                 List<UserProject> projects,
                                                 JobRequirementExtraction extraction) {
        List<List<String>> requiredSkillGroups = extraction.requiredSkillGroups();
        SkillMatchResult matchResult = skillScoreCalculator.matchDetailForGroups(skills, projects, requiredSkillGroups);
        return new RequiredSkillMatch(
                requiredSkillGroups.size(),
                skillScoreCalculator.matchedWithProjects(matchResult.matchedSkills(), projects),
                matchResult.unmatchedSkills(),
                experienceScoreCalculator.requiredSkillCount(requiredSkillGroups),
                experienceScoreCalculator.bestMatchedCount(projects, requiredSkillGroups)
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
