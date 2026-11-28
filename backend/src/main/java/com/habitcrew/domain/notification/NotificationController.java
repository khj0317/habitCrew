package com.habitcrew.domain.notification;

import com.habitcrew.global.security.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /** 최근 알림 30개와 안 읽은 개수 */
    @GetMapping("/api/notifications")
    public NotificationService.Inbox inbox(@LoginUserId Long userId) {
        return notificationService.inbox(userId);
    }

    @PostMapping("/api/notifications/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@LoginUserId Long userId) {
        notificationService.markAllRead(userId);
    }
}
