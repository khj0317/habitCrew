package com.habitcrew.domain.settlement;

import com.habitcrew.domain.crew.CrewMember;
import com.habitcrew.domain.crew.CrewMemberRepository;
import com.habitcrew.domain.settlement.dto.SettlementDtos;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CrewStatsService {

    static final int HISTORY_PAGE_DAYS = 14;
    private static final int STREAK_LOOKBACK_DAYS = 400;

    private final CrewMemberRepository memberRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ServiceTime serviceTime;

    /** 크루원별 연속 인증·총 인증·벌금. 쿼리 세 번으로 모든 크루원을 한꺼번에 계산한다 (N+1 없음) */
    @Transactional(readOnly = true)
    public SettlementDtos.CrewStats stats(Long userId, Long crewId) {
        CrewMember me = requireMember(crewId, userId);
        int certMask = me.getCrew().getCertDaysMask();
        LocalDate today = serviceTime.today();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("crewId", crewId)
                .addValue("since", today.minusDays(STREAK_LOOKBACK_DAYS));

        Map<Long, Set<LocalDate>> recentDates = new HashMap<>();
        jdbc.query("select user_id, cert_date from certifications where crew_id = :crewId and cert_date >= :since",
                params, rs -> {
                    recentDates.computeIfAbsent(rs.getLong(1), k -> new HashSet<>())
                            .add(rs.getObject(2, LocalDate.class));
                });

        Map<Long, Integer> totals = new HashMap<>();
        jdbc.query("select user_id, count(*) from certifications where crew_id = :crewId group by user_id",
                params, rs -> {
                    totals.put(rs.getLong(1), rs.getInt(2));
                });

        Map<Long, int[]> penalties = new HashMap<>(); // [빼먹은 횟수, 벌금 합계]
        jdbc.query("select user_id, count(*), coalesce(sum(points), 0) from penalties where crew_id = :crewId group by user_id",
                params, rs -> {
                    penalties.put(rs.getLong(1), new int[]{rs.getInt(2), rs.getInt(3)});
                });

        List<SettlementDtos.MemberStats> members = memberRepository.findMembers(crewId).stream()
                .map(m -> {
                    Long id = m.getUser().getId();
                    int[] penalty = penalties.getOrDefault(id, new int[2]);
                    int streak = Streaks.current(recentDates.getOrDefault(id, Set.of()), certMask, today,
                            serviceTime.dateOf(m.getJoinedAt()));
                    return new SettlementDtos.MemberStats(id, m.getUser().getNickname(), streak,
                            totals.getOrDefault(id, 0), penalty[0], penalty[1]);
                })
                .sorted(Comparator.comparingInt(SettlementDtos.MemberStats::currentStreak).reversed()
                        .thenComparing(Comparator.comparingInt(SettlementDtos.MemberStats::totalCertifications).reversed()))
                .toList();

        SettlementDtos.MemberStats mine = members.stream().filter(s -> s.userId().equals(userId)).findFirst().orElseThrow();
        return new SettlementDtos.CrewStats(mine, members);
    }

    /** 정산 내역 (최신 날짜부터 14일씩) */
    @Transactional(readOnly = true)
    public SettlementDtos.History history(Long userId, Long crewId, LocalDate before) {
        requireMember(crewId, userId);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("crewId", crewId)
                .addValue("before", before == null ? serviceTime.today().plusDays(1) : before)
                .addValue("limit", HISTORY_PAGE_DAYS + 1);

        Map<LocalDate, SettlementDtos.Settlement> byDate = new LinkedHashMap<>();
        jdbc.query("""
                        select settle_date, target_count, certified_count from crew_settlements
                        where crew_id = :crewId and settle_date < :before
                        order by settle_date desc
                        limit :limit
                        """,
                params, rs -> {
                    LocalDate date = rs.getObject(1, LocalDate.class);
                    byDate.put(date, new SettlementDtos.Settlement(date, rs.getInt(2), rs.getInt(3), new java.util.ArrayList<>()));
                });

        boolean hasNext = byDate.size() > HISTORY_PAGE_DAYS;
        List<SettlementDtos.Settlement> items = byDate.values().stream().limit(HISTORY_PAGE_DAYS).toList();
        if (!items.isEmpty()) {
            jdbc.query("""
                            select p.settle_date, p.user_id, u.nickname, p.points
                            from penalties p join users u on u.id = p.user_id
                            where p.crew_id = :crewId and p.settle_date between :from and :to
                            order by p.settle_date desc, u.nickname
                            """,
                    params.addValue("from", items.getLast().date()).addValue("to", items.getFirst().date()),
                    rs -> {
                        SettlementDtos.Settlement settlement = byDate.get(rs.getObject(1, LocalDate.class));
                        if (settlement != null) {
                            settlement.penalties().add(new SettlementDtos.Penalty(rs.getLong(2), rs.getString(3), rs.getInt(4)));
                        }
                    });
        }
        return new SettlementDtos.History(items, hasNext ? items.getLast().date() : null);
    }

    private CrewMember requireMember(Long crewId, Long userId) {
        return memberRepository.findByCrewIdAndUserId(crewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
    }
}
