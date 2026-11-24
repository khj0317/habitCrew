package com.habitcrew.domain.settlement.dto;

import java.time.LocalDate;
import java.util.List;

public final class SettlementDtos {

    private SettlementDtos() {
    }

    public record MemberStats(
            Long userId,
            String nickname,
            int currentStreak,
            int totalCertifications,
            int missedCount,
            int totalPenaltyPoints
    ) {
    }

    /** 크루원 기록. members는 연속 인증이 긴 순서 */
    public record CrewStats(MemberStats me, List<MemberStats> members) {
    }

    public record Penalty(Long userId, String nickname, int points) {
    }

    public record Settlement(LocalDate date, int targetCount, int certifiedCount, List<Penalty> penalties) {
    }

    /** 정산 내역 한 페이지. nextBefore가 null이면 마지막 페이지 */
    public record History(List<Settlement> items, LocalDate nextBefore) {
    }
}
