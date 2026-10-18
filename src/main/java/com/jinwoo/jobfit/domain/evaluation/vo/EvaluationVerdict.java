package com.jinwoo.jobfit.domain.evaluation.vo;

public enum EvaluationVerdict {
    FIT,
    UNFIT,
    // 기술/경험/우대/자격증 네 항목이 모두 평가 제외(비교할 정보 없음)인 경우
    INSUFFICIENT_INFO
}
