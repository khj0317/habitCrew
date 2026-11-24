package com.habitcrew.global.time;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * 요청이 서버에 도착한 시각을 기록한다.
 * 사진 업로드는 본문(최대 10MB)을 다 받기까지 시간이 걸린다. 23:59:50에 올리기 시작했는데 업로드가 끝난 시각(00:00:05)으로
 * 날짜를 정하면 어제 인증이 오늘 인증으로 바뀌어 버린다. 그래서 본문을 읽기 전(멀티파트 해석 전)인 필터 단계에서 시각을 잡아 둔다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTimeFilter extends OncePerRequestFilter {

    public static final String ATTRIBUTE = RequestTimeFilter.class.getName() + ".receivedAt";

    private final ServiceTime serviceTime;

    public RequestTimeFilter(ServiceTime serviceTime) {
        this.serviceTime = serviceTime;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(ATTRIBUTE, serviceTime.now());
        chain.doFilter(request, response);
    }

    public static Instant receivedAt(HttpServletRequest request, ServiceTime fallback) {
        Object value = request.getAttribute(ATTRIBUTE);
        return value instanceof Instant instant ? instant : fallback.now();
    }
}
