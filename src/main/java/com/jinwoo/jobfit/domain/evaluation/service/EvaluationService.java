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
        return evaluate(userProfile, skills, projects, certificates, evaluationWeight, jobPosting, new LlmCallBreaker());
    }

    // 여러 공고를 한 요청에서 평가할 때는 같은 breaker를 넘겨, LLM 연속 실패 시 남은 공고의 추출·근거 생성 호출을 건너뛴다.
    public EvaluationResult evaluate(UserProfile userProfile,
                                      List<UserSkill> skills,
                                      List<UserProject> projects,
                                      List<UserCertificate> certificates,
                                      EvaluationWeight evaluationWeight,
                                      JobPosting jobPosting,
                                      LlmCallBreaker breaker) {
        JobRequirementExtraction extraction = jobRequirementExtractor.extract(jobPosting, breaker);

        ConditionCheckResult conditionCheckResult = requiredConditionChecker.check(userProfile, jobPosting, extraction);
        ScoreResult scoreResult = scoreCalculator.calculate(skills, projects, certificates, extraction);
        EvaluationReasoning reasoning = evaluationReasoningGenerator.generate(
                jobPosting, extraction, userProfile, skills, projects, certificates, scoreResult, breaker);

        if (!conditionCheckResult.passed()) {
            return EvaluationResult.unfit(jobPosting.getId(), scoreResult, conditionCheckResult.failedReasons(),
                    conditionCheckResult.notices(), reasoning);
        }

        if (scoreResult.allExcluded()) {
            return EvaluationResult.insufficientInfo(jobPosting.getId(), scoreResult, conditionCheckResult.notices(), reasoning);
        }

        double totalScore = scoreCalculator.calculateTotalScore(scoreResult, evaluationWeight);

        return EvaluationResult.fit(jobPosting.getId(), totalScore, scoreResult, conditionCheckResult.notices(), reasoning);
    }
}
