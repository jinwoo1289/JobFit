package com.jinwoo.jobfit.domain.evaluation.vo;

// 공고 experienceLevel 문자열을 신입 지원 가능 여부 관점에서 분류한 결과.
// UNKNOWN은 jobCategory의 null과 같은 "판정 불가"로, 필수 조건 판정에서 UNFIT 사유로 쓰지 않는다.
public enum ExperienceLevelType {
    NEW_GRADUATE_ALLOWED,
    EXPERIENCED_ONLY,
    UNKNOWN
}
