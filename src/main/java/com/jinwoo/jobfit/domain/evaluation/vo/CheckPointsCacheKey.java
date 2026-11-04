package com.jinwoo.jobfit.domain.evaluation.vo;

/**
 * checkPoints 캐시를 재사용해도 되는지 판단하는 키.
 * profileHash는 근거 프롬프트에 들어가는 프로필 값(희망 직무·경력·보유 기술·프로젝트·자격증)의 해시라 프로필을 수정하면 바뀌고
 * (보유 기술·프로젝트별 사용 기술·자격증은 정렬해 해시하므로 순서만 바꾸면 바뀌지 않는다),
 * extractionHash는 추출 결과 내용의 해시라 프롬프트 버전 변경이든 강제 재추출이든 추출이 바뀌면 바뀐다.
 * 가중치는 근거 프롬프트에 들어가지 않아 checkPoints에 영향이 없으므로 키에 넣지 않는다.
 */
public record CheckPointsCacheKey(
        Long jobPostingId,
        Long userProfileId,
        String profileHash,
        String extractionHash,
        int reasoningPromptVersion
) {
}
