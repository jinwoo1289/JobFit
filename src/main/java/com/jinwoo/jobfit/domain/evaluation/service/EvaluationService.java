package com.jinwoo.jobfit.domain.evaluation.service;

import com.jinwoo.jobfit.domain.evaluation.vo.ConditionCheckResult;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.ScoreResult;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;
import com.jinwoo.jobfit.domain.user.entity.EvaluationWeight;
import com.jinwoo.jobfit.domain.user.entity.UserProfile;
import com.jinwoo.jobfit.domain.user.entity.UserProject;
import com.jinwoo.jobfit.domain.user.entity.UserSkill;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final RequiredConditionChecker requiredConditionChecker;
    private final ScoreCalculator scoreCalculator;

    public EvaluationResult evaluate(UserProfile userProfile,
                                      List<UserSkill> skills,
                                      List<UserProject> projects,
                                      EvaluationWeight evaluationWeight,
                                      JobPosting jobPosting) {
        ConditionCheckResult conditionCheckResult = requiredConditionChecker.check(userProfile, jobPosting);
        ScoreResult scoreResult = scoreCalculator.calculate(skills, projects, jobPosting);

        if (!conditionCheckResult.passed()) {
            return EvaluationResult.unfit(jobPosting.getId(), scoreResult, conditionCheckResult.failedReasons());
        }

        double totalScore = calculateTotalScore(scoreResult, evaluationWeight);

        return EvaluationResult.fit(jobPosting.getId(), totalScore, scoreResult);
    }

    private double calculateTotalScore(ScoreResult scoreResult, EvaluationWeight evaluationWeight) {
        int skillWeight = evaluationWeight.getSkillWeight();
        int experienceWeight = evaluationWeight.getExperienceWeight();
        int implementedWeight = skillWeight + experienceWeight;
        if (implementedWeight == 0) {
            return 0;
        }

        double normalizedSkillWeight = (double) skillWeight / implementedWeight;
        double normalizedExperienceWeight = (double) experienceWeight / implementedWeight;

        return scoreResult.skillScore() * normalizedSkillWeight
                + scoreResult.experienceScore() * normalizedExperienceWeight;
    }
}
