package com.habitcrew.domain.settlement;

import com.habitcrew.domain.settlement.dto.SettlementDtos;
import com.habitcrew.global.security.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
public class SettlementController {

    private final CrewStatsService statsService;

    @GetMapping("/api/crews/{crewId}/stats")
    public SettlementDtos.CrewStats stats(@LoginUserId Long userId, @PathVariable Long crewId) {
        return statsService.stats(userId, crewId);
    }

    @GetMapping("/api/crews/{crewId}/settlements")
    public SettlementDtos.History history(@LoginUserId Long userId, @PathVariable Long crewId,
                                          @RequestParam(required = false)
                                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate before) {
        return statsService.history(userId, crewId, before);
    }
}
