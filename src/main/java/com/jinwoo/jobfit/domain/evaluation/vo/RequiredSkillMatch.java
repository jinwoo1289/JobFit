package com.jinwoo.jobfit.domain.evaluation.vo;

import java.util.List;

// 점수 옆에 "필수 기술 N개 중 M개 보유"처럼 개수를 표시하기 위한 필수 기술 매칭 요약.
// 경험 개수는 경험 점수와 같은 기준(정규화 후 중복 제거한 필수 기술, 가장 많이 겹치는 프로젝트 하나)으로 센다.
public record RequiredSkillMatch(
        int requiredSkillCount,
        List<MatchedSkill> matchedSkills,
        List<String> missingSkills,
        int experienceSkillCount,
        int experienceMatchedCount
) {
}
