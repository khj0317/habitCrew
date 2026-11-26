package com.habitcrew.domain.push;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** 매일 22:00 (KST) 마감 알림. 여러 서버가 동시에 돌아도 notification_log 덕분에 한 번만 간다 */
@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@ConditionalOnProperty(name = "habitcrew.push.reminder-enabled", havingValue = "true")
public class ReminderScheduler {

    private final DeadlineReminder reminder;

    @Scheduled(cron = "${habitcrew.push.reminder-cron}", zone = "${habitcrew.zone}")
    public void remind() {
        try {
            reminder.sendToday();
        } catch (RuntimeException e) {
            log.error("마감 알림 실패", e);
        }
    }
}
