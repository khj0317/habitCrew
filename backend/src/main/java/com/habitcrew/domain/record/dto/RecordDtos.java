package com.habitcrew.domain.record.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public final class RecordDtos {

    private RecordDtos() {
    }

    public record RankingEntry(int rank, Long userId, String nickname, int count) {
    }

    public record Ranking(YearMonth month, List<RankingEntry> entries) {
    }

    public enum DayStatus {
        /** 인증함 */
        CERTIFIED,
        /** 인증일인데 빼먹음 */
        MISSED,
        /** 인증 요일이 아님 */
        REST,
        /** 오늘, 아직 인증 전 */
        PENDING,
        /** 아직 오지 않은 날 */
        FUTURE,
        /** 참여하기 전 (또는 참여한 첫날) */
        NOT_JOINED
    }

    public record Day(LocalDate date, DayStatus status) {
    }

    public record Calendar(
            YearMonth month,
            Long userId,
            String nickname,
            int certifiedCount,
            int missedCount,
            List<Day> days
    ) {
    }
}
