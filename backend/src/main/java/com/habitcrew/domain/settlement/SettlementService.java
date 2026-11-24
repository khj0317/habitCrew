package com.habitcrew.domain.settlement;

import com.habitcrew.domain.crew.CertDays;
import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 하루치 정산: 그날 인증하지 않은 크루원에게 벌금을 매긴다.
 *
 * <p>한 크루의 하루 정산은 트랜잭션 하나로 끝낸다. 시작할 때 (crew_id, settle_date) 정산 기록부터 넣는데,
 * 이미 있으면(다른 서버가 했거나, 재시작 후 다시 돌거나) 아무것도 하지 않는다. 그래서 몇 번을 돌려도
 * 벌금이 두 번 매겨지지 않는다(멱등). 동시에 두 곳에서 돌면 기본 키 때문에 뒤에 온 쪽이 앞의 트랜잭션이 끝날 때까지
 * 기다렸다가 "이미 있음"으로 끝난다.
 *
 * <p>대상 규칙
 * <ul>
 *   <li>그날이 크루의 인증 요일이어야 한다</li>
 *   <li>그날 0시 이전에 참여한 크루원만 대상이다. 그날 참여한 사람은 첫날이라 봐준다 (밤 11시 50분에 들어와 벌금을 받는 일이 없게)</li>
 *   <li>정산 전에 크루를 나간 사람은 대상이 아니다</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final NamedParameterJdbcTemplate jdbc;
    private final ServiceTime serviceTime;

    /** 이 날짜에 정산해야 하는(인증 요일이고, 그날 끝나기 전에 만들어졌고, 아직 정산하지 않은) 크루 */
    @Transactional(readOnly = true)
    public List<Long> crewsToSettle(LocalDate date) {
        int bit = 1 << (date.getDayOfWeek().getValue() - 1);
        return jdbc.queryForList("""
                        select c.id from crews c
                        where (c.cert_days & :bit) <> 0
                          and c.created_at < :endOfDay
                          and not exists (select 1 from crew_settlements s
                                          where s.crew_id = c.id and s.settle_date = :date)
                        order by c.id
                        """,
                new MapSqlParameterSource()
                        .addValue("bit", bit)
                        .addValue("endOfDay", Timestamp.from(serviceTime.deadlineOf(date)))
                        .addValue("date", date),
                Long.class);
    }

    /**
     * 한 크루의 하루를 정산한다.
     *
     * @return 이번 호출에서 실제로 정산했으면 true, 이미 정산돼 있었으면 false
     */
    @Transactional
    public boolean settle(Long crewId, LocalDate date) {
        if (!date.isBefore(serviceTime.today())) {
            throw new IllegalArgumentException("아직 끝나지 않은 날은 정산할 수 없습니다: " + date);
        }
        Map<String, Object> crew = jdbc.queryForList(
                "select cert_days, penalty_points from crews where id = :crewId",
                Map.of("crewId", crewId)).stream().findFirst().orElse(null);
        if (crew == null || !CertDays.contains((Integer) crew.get("cert_days"), date.getDayOfWeek())) {
            return false;
        }

        // 1) 정산 기록을 먼저 "찜"한다. 이미 있으면 다른 곳에서 정산한 것이므로 끝
        int claimed = jdbc.update("""
                        insert into crew_settlements (crew_id, settle_date, settled_at)
                        values (:crewId, :date, :now)
                        on conflict do nothing
                        """,
                new MapSqlParameterSource()
                        .addValue("crewId", crewId)
                        .addValue("date", date)
                        .addValue("now", Timestamp.from(serviceTime.now())));
        if (claimed == 0) {
            return false;
        }

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("crewId", crewId)
                .addValue("date", date)
                .addValue("startOfDay", Timestamp.from(serviceTime.deadlineOf(date.minusDays(1))))
                .addValue("points", crew.get("penalty_points"))
                .addValue("now", Timestamp.from(serviceTime.now()));

        // 2) 대상 = 그날 0시 이전에 참여한 크루원. 그중 그날 인증이 없는 사람에게 벌금 (한 문장으로 처리)
        int penalized = jdbc.update("""
                insert into penalties (crew_id, user_id, settle_date, points, created_at)
                select m.crew_id, m.user_id, :date, :points, :now
                from crew_members m
                where m.crew_id = :crewId
                  and m.joined_at < :startOfDay
                  and not exists (select 1 from certifications c
                                  where c.crew_id = m.crew_id and c.user_id = m.user_id and c.cert_date = :date)
                on conflict do nothing
                """, params);

        // 3) 결과 숫자를 기록해 둔다 (정산 내역 화면용)
        jdbc.update("""
                update crew_settlements set
                    target_count = (select count(*) from crew_members m
                                    where m.crew_id = :crewId and m.joined_at < :startOfDay),
                    certified_count = (select count(*) from crew_members m
                                       where m.crew_id = :crewId and m.joined_at < :startOfDay
                                         and exists (select 1 from certifications c
                                                     where c.crew_id = m.crew_id and c.user_id = m.user_id
                                                       and c.cert_date = :date)),
                    penalized_count = :penalized
                where crew_id = :crewId and settle_date = :date
                """, params.addValue("penalized", penalized));

        log.info("정산 완료: crew={} date={} 벌금 {}명", crewId, date, penalized);
        return true;
    }
}
