package com.jinwoo.jobfit.domain.evaluation.dto;

import com.jinwoo.jobfit.domain.evaluation.vo.DeadlineStatus;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationReasoning;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationResult;
import com.jinwoo.jobfit.domain.evaluation.vo.EvaluationVerdict;
import com.jinwoo.jobfit.domain.evaluation.vo.MatchedSkill;
import com.jinwoo.jobfit.domain.evaluation.vo.RequiredSkillMatch;
import com.jinwoo.jobfit.domain.job.entity.CloseType;
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
        // 우대 점수 근거: 우대 기술 preferredSkillCount개 중 preferredMatchedCount개 보유 (점수 분모 상한과 무관한 실제 개수)
        int preferredSkillCount,
        int preferredMatchedCount,
        // 필수 요건이 적어 기술·경험 가중치 일부를 우대로 옮겨 총점을 계산한 공고
        boolean thinRequired,
        // 지원 완료 체크 여부와 체크 시각(미체크면 null). 평가 결과·캐시와 무관하게 응답을 만들 때만 합친다.
        boolean applied,
        LocalDateTime appliedAt,
        // 마감일 표시와 마감 임박 안내용. 판정·점수·캐시에는 쓰지 않는다.
        LocalDateTime expirationAt,
        CloseType closeType,
        // 오늘 기준 마감일까지 남은 날 수(오늘 마감이면 0). 마감일이 없거나 이미 지났으면 null.
        Integer daysUntilDeadline,
        // 마감 D-3 이내(당일 포함)인 진행 중 공고
        boolean deadlineImminent
) {
    public static EvaluationResponse from(EvaluationResult result, JobPosting jobPosting, LocalDateTime appliedAt,
                                          DeadlineStatus deadlineStatus) {
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
                result.preferredSkillCount(),
                result.preferredMatchedCount(),
                result.thinRequired(),
                appliedAt != null,
                appliedAt,
                jobPosting.getExpirationAt(),
                jobPosting.getCloseType(),
                deadlineStatus.daysLeft(),
                deadlineStatus.imminent()
        );
    }
}
