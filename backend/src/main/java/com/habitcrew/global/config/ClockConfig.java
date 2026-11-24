package com.habitcrew.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 현재 시각은 항상 Clock 빈에서 가져온다.
 * 테스트에서 시계를 고정해 "23시 59분 인증", "자정 정산" 같은 경계 상황을 재현하기 위해서다.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
