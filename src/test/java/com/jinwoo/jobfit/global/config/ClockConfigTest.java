package com.jinwoo.jobfit.global.config;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ClockConfigTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    // 서버 OS 시간대가 UTC인 Render에서도 마감일(한국 시간 기준 LocalDateTime) 판정이 어긋나지 않도록,
    // 고정 시각이 없을 때도 clock의 zone은 Asia/Seoul이어야 한다.
    @Test
    void clock_withoutFixedInstant_usesSeoulZone() {
        Clock clock = new ClockConfig().clock("");

        assertThat(clock.getZone()).isEqualTo(SEOUL);
    }

    @Test
    void clock_withFixedInstant_usesSeoulZoneAndFixedInstant() {
        Clock clock = new ClockConfig().clock("2026-09-01T00:00:00Z");

        assertThat(clock.getZone()).isEqualTo(SEOUL);
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z"));
    }
}
