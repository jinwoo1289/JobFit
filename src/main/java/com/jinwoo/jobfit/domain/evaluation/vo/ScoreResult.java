package com.jinwoo.jobfit.domain.evaluation.vo;

// requiredSkillGroupCount: 필수 기술 단위 개수(단일 기술 + 택1 그룹). 가중치 이동 판단에 쓴다.
// preferredSkillCount/preferredMatchedCount: "우대 9개 중 3개 보유"처럼 표시할 실제 개수. 우대 점수의 분모 상한과 무관하다.
// thinRequired: 필수 요건이 적어 기술·경험 가중치 일부를 우대로 옮겨 총점을 계산하는 공고인지 여부.
public record ScoreResult(
        double skillScore, boolean skillExcluded,
        double experienceScore, boolean experienceExcluded,
        double preferenceScore, boolean preferenceExcluded,
        double certificateScore, boolean certificateExcluded,
        int requiredSkillGroupCount,
        int preferredSkillCount,
        int preferredMatchedCount,
        boolean thinRequired
) {
    public boolean allExcluded() {
        return skillExcluded && experienceExcluded && preferenceExcluded && certificateExcluded;
    }
}
