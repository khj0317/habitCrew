package com.habitcrew.domain.record;

import com.habitcrew.domain.crew.CertDays;
import com.habitcrew.domain.crew.CrewMember;
import com.habitcrew.domain.crew.CrewMemberRepository;
import com.habitcrew.domain.record.dto.RecordDtos;
import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 한 달 인증 달력(잔디). 날마다 상태를 하나씩 정한다.
 * 정산과 같은 규칙을 쓴다: 참여한 날은 인증하지 않아도 "빼먹음"이 아니다.
 */
@Service
@RequiredArgsConstructor
public class CalendarService {

    private final CrewMemberRepository memberRepository;
    private final NamedParameterJdbcTemplate jdbc;
    private final ServiceTime serviceTime;

    @Transactional(readOnly = true)
    public RecordDtos.Calendar calendar(Long userId, Long crewId, YearMonth month, Long targetUserId) {
        CrewMember me = memberRepository.findByCrewIdAndUserId(crewId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CREW_NOT_FOUND));
        CrewMember target = targetUserId == null || targetUserId.equals(userId) ? me
                : memberRepository.findByCrewIdAndUserId(crewId, targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MEMBER_NOT_FOUND));

        YearMonth targetMonth = month == null ? YearMonth.from(serviceTime.today()) : month;
        Set<LocalDate> certified = new HashSet<>(jdbc.queryForList("""
                        select cert_date from certifications
                        where crew_id = :crewId and user_id = :userId and cert_date between :from and :to
                        """,
                new MapSqlParameterSource()
                        .addValue("crewId", crewId)
                        .addValue("userId", target.getUser().getId())
                        .addValue("from", targetMonth.atDay(1))
                        .addValue("to", targetMonth.atEndOfMonth()),
                LocalDate.class));

        int mask = me.getCrew().getCertDaysMask();
        LocalDate today = serviceTime.today();
        LocalDate joined = serviceTime.dateOf(target.getJoinedAt());

        List<RecordDtos.Day> days = new ArrayList<>();
        for (LocalDate day = targetMonth.atDay(1); !day.isAfter(targetMonth.atEndOfMonth()); day = day.plusDays(1)) {
            days.add(new RecordDtos.Day(day, status(day, certified, mask, today, joined)));
        }
        int certifiedCount = (int) days.stream().filter(d -> d.status() == RecordDtos.DayStatus.CERTIFIED).count();
        int missedCount = (int) days.stream().filter(d -> d.status() == RecordDtos.DayStatus.MISSED).count();
        return new RecordDtos.Calendar(targetMonth, target.getUser().getId(), target.getUser().getNickname(),
                certifiedCount, missedCount, days);
    }

    static RecordDtos.DayStatus status(LocalDate day, Set<LocalDate> certified, int mask, LocalDate today,
                                       LocalDate joined) {
        if (certified.contains(day)) {
            return RecordDtos.DayStatus.CERTIFIED;
        }
        if (day.isAfter(today)) {
            return RecordDtos.DayStatus.FUTURE;
        }
        if (day.isBefore(joined)) {
            return RecordDtos.DayStatus.NOT_JOINED;
        }
        if (!CertDays.contains(mask, day.getDayOfWeek())) {
            return RecordDtos.DayStatus.REST;
        }
        if (day.equals(today)) {
            return RecordDtos.DayStatus.PENDING;
        }
        if (day.equals(joined)) {
            return RecordDtos.DayStatus.NOT_JOINED; // 참여한 날은 정산 대상이 아니다
        }
        return RecordDtos.DayStatus.MISSED;
    }
}
