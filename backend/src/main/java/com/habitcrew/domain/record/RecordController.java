package com.habitcrew.domain.record;

import com.habitcrew.domain.record.dto.RecordDtos;
import com.habitcrew.global.security.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
public class RecordController {

    private final RankingService rankingService;
    private final CalendarService calendarService;

    /** 월간 인증 랭킹. month 예: 2026-11 (없으면 이번 달) */
    @GetMapping("/api/crews/{crewId}/ranking")
    public RecordDtos.Ranking ranking(@LoginUserId Long userId, @PathVariable Long crewId,
                                      @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return rankingService.ranking(userId, crewId, month);
    }

    /** 한 달 인증 달력. userId가 없으면 내 달력 */
    @GetMapping("/api/crews/{crewId}/calendar")
    public RecordDtos.Calendar calendar(@LoginUserId Long userId, @PathVariable Long crewId,
                                        @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                        @RequestParam(name = "userId", required = false) Long targetUserId) {
        return calendarService.calendar(userId, crewId, month, targetUserId);
    }
}
