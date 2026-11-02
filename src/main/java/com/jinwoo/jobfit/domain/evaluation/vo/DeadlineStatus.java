package com.jinwoo.jobfit.domain.evaluation.vo;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

// 화면의 마감 임박 안내용. 판정·점수·캐시에는 쓰지 않는다.
// daysLeft는 오늘 기준 마감일까지 남은 날 수(오늘 마감이면 0). 마감일이 없거나 이미 지났으면 null.
public record DeadlineStatus(Integer daysLeft, boolean imminent) {

    // 마감 D-3부터 당일까지를 임박으로 본다.
    static final int IMMINENT_DAYS = 3;

    private static final DeadlineStatus NONE = new DeadlineStatus(null, false);

    public static DeadlineStatus of(LocalDateTime expirationAt, LocalDateTime now) {
        // 마감 판정(RequiredConditionChecker)과 같은 기준: 마감 시각이 현재보다 이전이면 지난 공고.
        if (expirationAt == null || expirationAt.isBefore(now)) {
            return NONE;
        }
        int daysLeft = (int) ChronoUnit.DAYS.between(now.toLocalDate(), expirationAt.toLocalDate());
        return new DeadlineStatus(daysLeft, daysLeft <= IMMINENT_DAYS);
    }
}
