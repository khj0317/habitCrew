package com.habitcrew.global.time;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 서비스 기준 시간대(KST)의 "오늘"을 계산한다.
 * 서버는 UTC로 돌아가도(배포 환경) 인증 날짜와 마감은 항상 한국 시간 기준이어야 하기 때문에 한곳에서만 계산한다.
 */
@Component
public class ServiceTime {

    private final Clock clock;
    private final ZoneId zone;

    public ServiceTime(Clock clock, @Value("${habitcrew.zone}") String zone) {
        this.clock = clock;
        this.zone = ZoneId.of(zone);
    }

    public Instant now() {
        return clock.instant();
    }

    public LocalDate today() {
        return dateOf(clock.instant());
    }

    public LocalDate dateOf(Instant instant) {
        return instant.atZone(zone).toLocalDate();
    }

    /** 그날의 인증 마감 시각 = 다음 날 0시 (KST) */
    public Instant deadlineOf(LocalDate date) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public ZoneId zone() {
        return zone;
    }
}
