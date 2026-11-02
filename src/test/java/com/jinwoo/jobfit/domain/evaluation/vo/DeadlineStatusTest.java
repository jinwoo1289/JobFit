package com.jinwoo.jobfit.domain.evaluation.vo;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DeadlineStatusTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 2, 15, 0);

    @Test
    void of_deadlineLaterToday_isZeroDaysLeftAndImminent() {
        DeadlineStatus status = DeadlineStatus.of(LocalDateTime.of(2026, 11, 2, 23, 59, 59), NOW);

        assertThat(status.daysLeft()).isZero();
        assertThat(status.imminent()).isTrue();
    }

    @Test
    void of_deadlineInThreeDays_isImminent() {
        DeadlineStatus status = DeadlineStatus.of(LocalDateTime.of(2026, 11, 5, 23, 59, 59), NOW);

        assertThat(status.daysLeft()).isEqualTo(3);
        assertThat(status.imminent()).isTrue();
    }

    @Test
    void of_deadlineInFourDays_isNotImminent() {
        DeadlineStatus status = DeadlineStatus.of(LocalDateTime.of(2026, 11, 6, 23, 59, 59), NOW);

        assertThat(status.daysLeft()).isEqualTo(4);
        assertThat(status.imminent()).isFalse();
    }

    // 마감 시각이 지났으면 같은 날이라도 남은 날 수를 주지 않는다(마감 공고는 expired로 따로 표시).
    @Test
    void of_deadlinePassedEarlierToday_hasNoDaysLeft() {
        DeadlineStatus status = DeadlineStatus.of(LocalDateTime.of(2026, 11, 2, 9, 0), NOW);

        assertThat(status.daysLeft()).isNull();
        assertThat(status.imminent()).isFalse();
    }

    @Test
    void of_noDeadline_hasNoDaysLeft() {
        DeadlineStatus status = DeadlineStatus.of(null, NOW);

        assertThat(status.daysLeft()).isNull();
        assertThat(status.imminent()).isFalse();
    }
}
