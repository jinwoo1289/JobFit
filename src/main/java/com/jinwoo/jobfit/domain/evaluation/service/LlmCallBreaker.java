package com.jinwoo.jobfit.domain.evaluation.service;

/**
 * 평가 요청 하나 동안 요구사항 추출 LLM 호출의 연속 실패를 센다.
 * 크레딧 소진(429 insufficient_quota)처럼 다시 불러도 실패할 상황에서, 남은 공고마다 LLM을 호출하지 않고
 * 바로 실패 경로(옛 버전 캐시 또는 정규식 fallback)로 보내기 위함이다. 요청마다 새로 만들어 쓰고 공유하지 않는다.
 */
public class LlmCallBreaker {

    static final int CONSECUTIVE_FAILURE_THRESHOLD = 2;

    private int consecutiveFailures;

    public boolean isOpen() {
        return consecutiveFailures >= CONSECUTIVE_FAILURE_THRESHOLD;
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
    }

    public void recordFailure() {
        consecutiveFailures++;
    }
}
