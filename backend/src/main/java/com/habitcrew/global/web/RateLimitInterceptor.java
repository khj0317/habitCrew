package com.habitcrew.global.web;

import com.habitcrew.global.error.BusinessException;
import com.habitcrew.global.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Clock;
import java.time.Duration;

/**
 * @RateLimited가 붙은 API의 호출 수를 Redis로 센다 (고정 창 방식: 1분 단위 칸마다 INCR).
 * 서버가 여러 대여도 같은 Redis를 보므로 합쳐서 센다. 넘으면 429와 함께 몇 초 뒤에 다시 하면 되는지(Retry-After) 알려 준다.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final boolean enabled;

    public RateLimitInterceptor(StringRedisTemplate redis, Clock clock,
                                @Value("${habitcrew.rate-limit.enabled:true}") boolean enabled) {
        this.redis = redis;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!enabled || !(handler instanceof HandlerMethod method)) {
            return true;
        }
        RateLimited limit = method.getMethodAnnotation(RateLimited.class);
        if (limit == null) {
            return true;
        }
        long now = clock.millis() / 1000;
        long window = now / limit.windowSeconds();
        String key = "rate:" + limit.name() + ":" + who(request) + ":" + window;
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1) {
            redis.expire(key, Duration.ofSeconds(limit.windowSeconds() + 5));
        }
        if (count != null && count > limit.limit()) {
            long retryAfter = (window + 1) * limit.windowSeconds() - now;
            response.setHeader("Retry-After", String.valueOf(Math.max(1, retryAfter)));
            throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS,
                    "요청이 너무 잦아요. " + Math.max(1, retryAfter) + "초 뒤에 다시 시도해 주세요.");
        }
        return true;
    }

    private static String who(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "user:" + auth.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }
}
