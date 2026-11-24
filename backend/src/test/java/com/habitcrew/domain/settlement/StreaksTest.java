package com.habitcrew.domain.settlement;

import com.habitcrew.domain.crew.CertDays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StreaksTest {

    // 2030-01-07 월요일
    private static final LocalDate MON = LocalDate.of(2030, 1, 7);
    private static final LocalDate WED = MON.plusDays(2);
    private static final LocalDate FRI = MON.plusDays(4);
    private static final LocalDate JOINED = MON.minusDays(30);
    private static final int MON_WED_FRI = CertDays.toMask(List.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY));
    private static final int EVERY_DAY = CertDays.ALL;

    @Test
    @DisplayName("쉬는 요일은 건너뛰고 인증 요일만 센다")
    void skipsRestDays() {
        assertThat(Streaks.current(Set.of(MON, WED, FRI), MON_WED_FRI, FRI, JOINED)).isEqualTo(3);
    }

    @Test
    @DisplayName("오늘 아직 인증하지 않았으면 끊긴 게 아니다 (어제까지 센다)")
    void todayNotYetIsNotBroken() {
        assertThat(Streaks.current(Set.of(MON, WED), MON_WED_FRI, FRI, JOINED)).isEqualTo(2);
    }

    @Test
    @DisplayName("지난 인증일을 하나라도 빼먹으면 거기서 끊긴다")
    void brokenByMissedDay() {
        assertThat(Streaks.current(Set.of(MON, FRI), MON_WED_FRI, FRI, JOINED)).isEqualTo(1);
        assertThat(Streaks.current(Set.of(MON, WED), MON_WED_FRI, FRI.plusDays(3), JOINED)).isZero();
    }

    @Test
    @DisplayName("참여하기 전 날짜는 보지 않는다")
    void stopsAtJoinDate() {
        // 수요일에 참여: 화요일 이전은 보지 않으므로 수·목·금만 센다. 목요일을 빼먹어서 오늘(금) 하나만 이어진다
        assertThat(Streaks.current(Set.of(WED, FRI), EVERY_DAY, FRI, WED)).isEqualTo(1);
        // 수요일에 참여해 수·목·금 모두 인증: 참여 전(월·화)에 기록이 없어도 3일 연속
        assertThat(Streaks.current(Set.of(WED, WED.plusDays(1), FRI), EVERY_DAY, FRI, WED)).isEqualTo(3);
        assertThat(Streaks.current(Set.of(MON, MON.plusDays(1)), EVERY_DAY, MON.plusDays(1), MON)).isEqualTo(2);
    }

    @Test
    @DisplayName("인증 기록이 없으면 0")
    void empty() {
        assertThat(Streaks.current(Set.of(), MON_WED_FRI, FRI, JOINED)).isZero();
    }
}
