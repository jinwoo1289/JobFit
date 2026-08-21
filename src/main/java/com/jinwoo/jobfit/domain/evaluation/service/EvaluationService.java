package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ConditionCheckResult;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.JobRequirementExtraction;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserCertificate;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private static final int TOTAL_WEIGHT = 100;

    private final RequiredConditionChecker requiredConditionChecker;
    private final ScoreCalculator scoreCalculator;
    private final JobRequirementExtractor jobRequirementExtractor;
    private final EvaluationReasoningGenerator evaluationReasoningGenerator;

    public EvaluationResult evaluate(UserProfile userProfile,
                                      List<UserSkill> skills,
                                      List<UserProject> projects,
                                      List<UserCertificate> certificates,
                                      EvaluationWeight evaluationWeight,
                                      JobPosting jobPosting) {
        JobRequirementExtraction extraction = jobRequirementExtractor.extract(jobPosting);

        ConditionCheckResult conditionCheckResult = requiredConditionChecker.check(userProfile, jobPosting, extraction);
        ScoreResult scoreResult = scoreCalculator.calculate(skills, projects, certificates, extraction);
        EvaluationReasoning reasoning = evaluationReasoningGenerator.generate(
                jobPosting, extraction, userProfile, skills, projects, certificates, scoreResult);

        if (!conditionCheckResult.passed()) {
            return EvaluationResult.unfit(jobPosting.getId(), scoreResult, conditionCheckResult.failedReasons(), reasoning);
        }

        double totalScore = calculateTotalScore(scoreResult, evaluationWeight);

        return EvaluationResult.fit(jobPosting.getId(), totalScore, scoreResult, reasoning);
    }

    private double calculateTotalScore(ScoreResult scoreResult, EvaluationWeight evaluationWeight) {
        return (scoreResult.skillScore() * evaluationWeight.getSkillWeight()
                + scoreResult.experienceScore() * evaluationWeight.getExperienceWeight()
                + scoreResult.preferenceScore() * evaluationWeight.getPreferenceWeight()
                + scoreResult.certificateScore() * evaluationWeight.getCertificateWeight())
                / TOTAL_WEIGHT;
    }
}
