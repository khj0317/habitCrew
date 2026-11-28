package com.habitcrew.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * @Async 사용 (알림·메일처럼 응답을 기다릴 필요 없는 일).
 * 스레드 풀은 Spring Boot 기본 설정(applicationTaskExecutor, 가상 스레드 아님)을 쓴다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
