package com.habitcrew.domain.push;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import com.habitcrew.global.security.LoginUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/push")
@RequiredArgsConstructor
public class PushController {

    private final PushService pushService;

    /** 브라우저 PushSubscription.toJSON() 모양 그대로 받는다 */
    public record SubscriptionRequest(
            @NotBlank @Size(max = 1000) String endpoint,
            @NotNull @Valid Keys keys
    ) {
        public record Keys(@NotBlank @Size(max = 200) String p256dh, @NotBlank @Size(max = 100) String auth) {
        }
    }

    public record EndpointRequest(@NotBlank String endpoint) {
    }

    @GetMapping("/public-key")
    public java.util.Map<String, String> publicKey() {
        return java.util.Map.of("publicKey", pushService.publicKey());
    }

    @GetMapping("/status")
    public java.util.Map<String, Integer> status(@LoginUserId Long userId) {
        return java.util.Map.of("subscriptionCount", pushService.subscriptionCount(userId));
    }

    @PostMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void subscribe(@LoginUserId Long userId, @Valid @RequestBody SubscriptionRequest request) {
        pushService.subscribe(userId, request.endpoint(), request.keys().p256dh(), request.keys().auth());
    }

    @DeleteMapping("/subscriptions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsubscribe(@LoginUserId Long userId, @Valid @RequestBody EndpointRequest request) {
        pushService.unsubscribe(userId, request.endpoint());
    }

    /** 내 기기들로 시험 알림을 보낸다 */
    @PostMapping("/test")
    public java.util.Map<String, Integer> test(@LoginUserId Long userId) {
        if (pushService.subscriptionCount(userId) == 0) {
            throw new BusinessException(ErrorCode.PUSH_NOT_SUBSCRIBED);
        }
        int delivered = pushService.sendToUser(userId, new PushService.Message(
                "HabitCrew 알림이 켜졌어요 🔔", "인증 마감 2시간 전에 이렇게 알려 드릴게요.", "/", "test"));
        return java.util.Map.of("delivered", delivered);
    }
}
