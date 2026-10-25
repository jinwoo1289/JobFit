package com.jinwoo.jobfit.global.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Configuration
public class ClockConfig {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock(@Value("${jobfit.clock.fixed-instant:}") String fixedInstant) {
        Clock clock = StringUtils.hasText(fixedInstant)
                ? Clock.fixed(Instant.parse(fixedInstant), ZONE)
                : Clock.system(ZONE);

        // 고정 clock이 방치되어 마감일 판정 등이 실제 시각과 어긋나는 사고를 빨리 알아챌 수 있도록,
        // 기동 시 실제로 사용 중인 clock의 현재 시각을 한 줄 남긴다.
        log.info("Clock initialized: now={}", LocalDateTime.now(clock));

        return clock;
    }
}
