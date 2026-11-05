package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.EffectiveWeights;
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
    // 필수 기술 단위(단일 기술 + 택1 그룹)가 이 개수 미만이면 필수 요건이 적은 공고로 보고,
    // 기술·경험 가중치에 (단위 수 / THIN_REQUIRED_THRESHOLD)를 곱한 뒤 줄어든 만큼을 우대 가중치로 옮긴다.
    static final int THIN_REQUIRED_THRESHOLD = 3;

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
        SkillScore preference = skillScoreCalculator.calculatePreference(skills, projects, extraction.preferredSkills());
        double experienceScore = experienceScoreCalculator.calculateForGroups(projects, requiredSkillGroups);
        double certificateScore = certificateScoreCalculator.calculate(
                certificates, extraction.requiredCertificates(), extraction.preferredCertificates());

        // 경험 점수는 필수 기술(택1 그룹 포함)을 기준으로 계산하므로 skill과 동일한 조건으로 평가 제외된다.
        boolean experienceExcluded = !extraction.hasRequiredSkills();
        // 필수·우대 자격증이 모두 비어 있으면 비교할 자격증 요건 자체가 없는 것이므로 평가 제외한다.
        boolean certificateExcluded = extraction.requiredCertificates().isEmpty() && extraction.preferredCertificates().isEmpty();

        int requiredSkillGroupCount = requiredSkillGroups.size();
        int preferredMatchedCount = skillScoreCalculator.matchDetail(skills, projects, extraction.preferredSkills())
                .matchedSkills().size();

        return new ScoreResult(
                skill.value(), skill.excluded(),
                experienceScore, experienceExcluded,
                preference.value(), preference.excluded(),
                certificateScore, certificateExcluded,
                requiredSkillGroupCount,
                extraction.preferredSkills().size(),
                preferredMatchedCount,
                isThinRequired(requiredSkillGroupCount, preference.excluded())
        );
    }

    // 필수 0개는 기술·경험이 평가 제외되어 기존 재배분으로 처리하므로 대상이 아니다.
    // 우대 기술도 없으면 옮겨 받을 항목이 없으므로 가중치를 이동하지 않는다.
    private boolean isThinRequired(int requiredSkillGroupCount, boolean preferenceExcluded) {
        return requiredSkillGroupCount > 0
                && requiredSkillGroupCount < THIN_REQUIRED_THRESHOLD
                && !preferenceExcluded;
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

    public double calculateTotalScore(ScoreResult scoreResult, EvaluationWeight evaluationWeight) {
        EffectiveWeights weights = effectiveWeights(scoreResult, evaluationWeight);
        return (scoreResult.skillScore() * weights.skill()
                + scoreResult.experienceScore() * weights.experience()
                + scoreResult.preferenceScore() * weights.preference()
                + scoreResult.certificateScore() * weights.certificate()) / TOTAL_WEIGHT;
    }

    // 1) 필수 요건이 적은 공고(thinRequired)는 기술·경험 가중치에 (필수 단위 수 / 3)을 곱하고 줄어든 만큼을 우대로 옮긴다.
    //    (예: 필수 1개, 40/30/20/10 → 13.3/10/66.7/10)
    // 2) 평가 제외된 항목의 가중치를, 나머지 평가 대상 항목에 비율을 유지한 채로 재분배해 합계를 100으로 맞춘다.
    //    (예: 우대 기술이 없으면 preferenceWeight를 skill/experience/certificate에 배분)
    public EffectiveWeights effectiveWeights(ScoreResult scoreResult, EvaluationWeight evaluationWeight) {
        double skillWeight = evaluationWeight.getSkillWeight();
        double experienceWeight = evaluationWeight.getExperienceWeight();
        double preferenceWeight = evaluationWeight.getPreferenceWeight();
        double certificateWeight = evaluationWeight.getCertificateWeight();

        if (scoreResult.thinRequired()) {
            double ratio = (double) scoreResult.requiredSkillGroupCount() / THIN_REQUIRED_THRESHOLD;
            double moved = (skillWeight + experienceWeight) * (1 - ratio);
            skillWeight *= ratio;
            experienceWeight *= ratio;
            preferenceWeight += moved;
        }

        skillWeight = scoreResult.skillExcluded() ? 0 : skillWeight;
        experienceWeight = scoreResult.experienceExcluded() ? 0 : experienceWeight;
        preferenceWeight = scoreResult.preferenceExcluded() ? 0 : preferenceWeight;
        certificateWeight = scoreResult.certificateExcluded() ? 0 : certificateWeight;

        double activeWeightSum = skillWeight + experienceWeight + preferenceWeight + certificateWeight;
        if (activeWeightSum == 0) {
            return EffectiveWeights.none();
        }
        double scale = TOTAL_WEIGHT / activeWeightSum;
        return new EffectiveWeights(
                skillWeight * scale, experienceWeight * scale, preferenceWeight * scale, certificateWeight * scale);
    }
}
