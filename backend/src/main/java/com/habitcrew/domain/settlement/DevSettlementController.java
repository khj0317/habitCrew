package com.habitcrew.domain.settlement;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.time.ServiceTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 로컬 개발 전용: 자정을 기다리지 않고 정산을 바로 돌려 본다. local 프로필에서만 켜진다 (배포에는 없음).
 */
@Profile("local")
@RestController
@RequiredArgsConstructor
public class DevSettlementController {

    private final SettlementRunner runner;
    private final ServiceTime serviceTime;

    /** date가 없으면 밀린 정산을 모두, 있으면 그날만 정산한다 */
    @PostMapping("/api/dev/settlements/run")
    public SettlementRunner.Result run(@RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            return runner.runPending();
        }
        if (!date.isBefore(serviceTime.today())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "아직 끝나지 않은 날은 정산할 수 없어요.");
        }
        return runner.runFor(date);
    }
}
