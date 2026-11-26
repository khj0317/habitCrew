package com.habitcrew.domain.push;

import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 인증 마감 2시간 전(22:00 KST) 알림: 오늘이 인증일인데 아직 인증하지 않은 크루가 있는 사람에게 한 번 보낸다.
 * 크루가 여러 개여도 사람당 알림은 하나로 묶는다 (알림 폭탄 방지).
 * notification_log의 기본 키 (user_id, kind, ref_date)로 "보냄"을 먼저 기록한 사람에게만 보내서,
 * 서버가 여러 대이거나 작업이 다시 돌아도 같은 날 두 번 가지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeadlineReminder {

    static final String KIND = "DEADLINE_REMINDER";

    private final NamedParameterJdbcTemplate jdbc;
    private final PushService pushService;
    private final ServiceTime serviceTime;

    /** @return 알림을 보낸 사람 수 */
    public int sendToday() {
        LocalDate today = serviceTime.today();
        int bit = 1 << (today.getDayOfWeek().getValue() - 1);
        List<Map<String, Object>> targets = jdbc.queryForList("""
                        select m.user_id, count(*) as crews, min(c.name) as first_crew, min(c.id) as first_crew_id
                        from crew_members m
                        join crews c on c.id = m.crew_id
                        where (c.cert_days & :bit) <> 0
                          and not exists (select 1 from certifications x
                                          where x.crew_id = m.crew_id and x.user_id = m.user_id and x.cert_date = :today)
                          and exists (select 1 from push_subscriptions s where s.user_id = m.user_id)
                        group by m.user_id
                        """,
                new MapSqlParameterSource().addValue("bit", bit).addValue("today", today));

        int sent = 0;
        for (Map<String, Object> target : targets) {
            Long userId = ((Number) target.get("user_id")).longValue();
            int claimed = jdbc.update("""
                            insert into notification_log (user_id, kind, ref_date) values (:userId, :kind, :today)
                            on conflict do nothing
                            """,
                    new MapSqlParameterSource().addValue("userId", userId).addValue("kind", KIND).addValue("today", today));
            if (claimed == 0) {
                continue;
            }
            long crews = ((Number) target.get("crews")).longValue();
            String body = crews == 1
                    ? "'" + target.get("first_crew") + "' 인증이 아직이에요. 자정 전에 올려 주세요!"
                    : "'" + target.get("first_crew") + "' 외 " + (crews - 1) + "개 크루 인증이 남았어요.";
            String url = crews == 1 ? "/crews/" + target.get("first_crew_id") : "/";
            if (pushService.sendToUser(userId, new PushService.Message("⏰ 인증 마감 2시간 전", body, url, "deadline-" + today)) > 0) {
                sent++;
            }
        }
        log.info("마감 알림: 대상 {}명, 보냄 {}명", targets.size(), sent);
        return sent;
    }
}
