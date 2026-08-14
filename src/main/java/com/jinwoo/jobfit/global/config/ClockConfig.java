package com.jinwoo.jobfit.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

@Configuration
public class ClockConfig {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock(@Value("${jobfit.clock.fixed-instant:}") String fixedInstant) {
        if (StringUtils.hasText(fixedInstant)) {
            return Clock.fixed(Instant.parse(fixedInstant), ZONE);
        }
        return Clock.systemDefaultZone();
    }
}
