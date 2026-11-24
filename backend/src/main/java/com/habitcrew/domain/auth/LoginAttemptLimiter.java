package com.habitcrew.domain.auth;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 비밀번호 무차별 대입을 막는다: 같은 이메일로 연속 N번 틀리면 일정 시간 로그인을 막는다.
 * 실패 횟수는 Redis INCR로 세서 서버가 여러 대여도 공유되고, 잠금 시간이 지나면 키가 사라진다.
 */
@Component
public class LoginAttemptLimiter {

    private static final String KEY_PREFIX = "auth:login-fail:";

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration lockDuration;

    public LoginAttemptLimiter(StringRedisTemplate redisTemplate,
                               @Value("${habitcrew.login-limit.max-attempts}") int maxAttempts,
                               @Value("${habitcrew.login-limit.lock-duration}") Duration lockDuration) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.lockDuration = lockDuration;
    }

    public void checkNotLocked(String email) {
        String count = redisTemplate.opsForValue().get(KEY_PREFIX + email);
        if (count != null && Integer.parseInt(count) >= maxAttempts) {
            Long seconds = redisTemplate.getExpire(KEY_PREFIX + email);
            long minutes = seconds == null || seconds <= 0 ? 1 : (seconds + 59) / 60;
            throw new BusinessException(ErrorCode.LOGIN_LOCKED,
                    "로그인 시도가 너무 많아요. " + minutes + "분 뒤에 다시 시도해 주세요.");
        }
    }

    public void recordFailure(String email) {
        String key = KEY_PREFIX + email;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, lockDuration);
        }
    }

    public void reset(String email) {
        redisTemplate.delete(KEY_PREFIX + email);
    }
}
