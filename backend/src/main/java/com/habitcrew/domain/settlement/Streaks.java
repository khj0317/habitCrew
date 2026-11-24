package com.habitcrew.domain.settlement;

import com.habitcrew.domain.crew.CertDays;

import java.time.LocalDate;
import java.util.Set;

/**
 * 연속 인증 일수(스트릭) 계산.
 * 인증 요일만 센다: 월·수·금 크루라면 월·수·금을 모두 인증했을 때 3일 연속이다 (화·목은 쉬는 날이라 끊기지 않음).
 */
public final class Streaks {

    private static final int MAX_LOOKBACK_DAYS = 400;

    private Streaks() {
    }

    /**
     * @param certDates  이 사람이 인증한 날짜들
     * @param certMask   크루 인증 요일
     * @param today      오늘 (KST)
     * @param joinedDate 크루에 참여한 날. 이전 날짜는 보지 않는다
     */
    public static int current(Set<LocalDate> certDates, int certMask, LocalDate today, LocalDate joinedDate) {
        // 오늘은 아직 끝나지 않았으므로, 오늘 인증했으면 세고 안 했으면 어제부터 센다 (아직 끊긴 게 아님)
        LocalDate day = certDates.contains(today) ? today : today.minusDays(1);
        int streak = 0;
        for (int i = 0; i < MAX_LOOKBACK_DAYS && !day.isBefore(joinedDate); i++, day = day.minusDays(1)) {
            if (!CertDays.contains(certMask, day.getDayOfWeek())) {
                continue;
            }
            if (!certDates.contains(day)) {
                break;
            }
            streak++;
        }
        return streak;
    }
}
