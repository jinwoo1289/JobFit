package com.jinwoo.jobfit.domain.evaluation.dto;

import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationVerdict;
import com.jinwoo.jobfit.domain.evaluation.vo.MatchedSkill;
import com.jinwoo.jobfit.domain.evaluation.vo.RequiredSkillMatch;
import com.jinwoo.jobfit.domain.job.entity.JobPosting;

import java.time.LocalDateTime;
import java.util.List;

public record EvaluationResponse(
        Long jobPostingId,
        String companyName,
        String title,
        String originalUrl,
        double totalScore,
        double skillScore, boolean skillExcluded,
        double experienceScore, boolean experienceExcluded,
        double preferenceScore, boolean preferenceExcluded,
        double certificateScore, boolean certificateExcluded,
        EvaluationVerdict verdict,
        List<String> failedReasons,
        List<String> notices,
        EvaluationReasoning reasoning,
        // 마감 여부. 화면은 failedReasons 문자열이 아니라 이 값으로 마감 공고를 분리한다.
        boolean expired,
        int requiredSkillCount,
        int matchedSkillCount,
        int experienceSkillCount,
        int experienceMatchedCount,
        List<MatchedSkill> matchedSkills,
        List<String> missingSkills,
        // 지원 완료 체크 여부와 체크 시각(미체크면 null). 평가 결과·캐시와 무관하게 응답을 만들 때만 합친다.
        boolean applied,
        LocalDateTime appliedAt
) {
    public static EvaluationResponse from(EvaluationResult result, JobPosting jobPosting, LocalDateTime appliedAt) {
        RequiredSkillMatch skillMatch = result.skillMatch();
        return new EvaluationResponse(
                result.jobPostingId(),
                jobPosting.getCompanyName(),
                jobPosting.getTitle(),
                jobPosting.getOriginalUrl(),
                result.totalScore(),
                result.skillScore(), result.skillExcluded(),
                result.experienceScore(), result.experienceExcluded(),
                result.preferenceScore(), result.preferenceExcluded(),
                result.certificateScore(), result.certificateExcluded(),
                result.verdict(),
                result.failedReasons(),
                result.notices(),
                result.reasoning(),
                result.expired(),
                skillMatch.requiredSkillCount(),
                skillMatch.matchedSkills().size(),
                skillMatch.experienceSkillCount(),
                skillMatch.experienceMatchedCount(),
                skillMatch.matchedSkills(),
                skillMatch.missingSkills(),
                appliedAt != null,
                appliedAt
        );
    }
}
