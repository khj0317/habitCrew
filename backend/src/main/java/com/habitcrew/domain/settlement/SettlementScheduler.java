package com.habitcrew.domain.settlement;

import com.habitcrew.domain.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * 정산 작업을 시간에 맞춰 부른다. habitcrew.settlement.enabled=false면 켜지지 않는다 (테스트에서는 직접 부른다).
 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "habitcrew.settlement.enabled", havingValue = "true")
public class SettlementScheduler {

    private final SettlementRunner runner;
    private final NotificationService notificationService;

    /** 매일 00:05 (KST). 자정 직전에 올라온 업로드가 끝날 시간을 조금 둔다 */
    @Scheduled(cron = "${habitcrew.settlement.cron}", zone = "${habitcrew.zone}")
    public void daily() {
        run("정기 정산");
        try {
            int removed = notificationService.deleteOld();
            if (removed > 0) {
                log.info("오래된 알림 {}개를 정리했습니다", removed);
            }
        } catch (RuntimeException e) {
            log.error("오래된 알림 정리 실패", e);
        }
    }

    /** 서버가 꺼져 있던 동안 놓친 정산을 서버가 뜨자마자 따라잡는다 */
    @EventListener(ApplicationReadyEvent.class)
    public void catchUp() {
        run("시작 시 따라잡기");
    }

    private void run(String reason) {
        try {
            SettlementRunner.Result result = runner.runPending();
            if (result.ran()) {
                log.info("{}: 크루 {}곳 정산", reason, result.settledCount());
            }
        } catch (RuntimeException e) {
            log.error("{} 실패", reason, e);
        }
    }
}
