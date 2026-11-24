package com.habitcrew.domain.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * 리프레시 토큰을 JavaScript가 읽을 수 없는 HttpOnly 쿠키로 주고받는다.
 * - SameSite=Strict: 다른 사이트에서 시작된 요청에는 쿠키가 실리지 않아 CSRF로 토큰을 재발급받을 수 없다
 * - Path=/api/auth: 재발급·로그아웃 요청에만 실린다 (다른 API 요청마다 토큰이 오가지 않게)
 */
@Component
public class RefreshTokenCookie {

    public static final String NAME = "habitcrew_refresh";
    private static final String PATH = "/api/auth";

    private final boolean secure;
    private final Duration ttl;

    public RefreshTokenCookie(@Value("${habitcrew.auth.cookie-secure:true}") boolean secure,
                              @Value("${jwt.refresh-token-ttl}") Duration ttl) {
        this.secure = secure;
        this.ttl = ttl;
    }

    public Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        return cookies == null ? Optional.empty() : Arrays.stream(cookies)
                .filter(cookie -> NAME.equals(cookie.getName()) && !cookie.getValue().isBlank())
                .map(Cookie::getValue)
                .findFirst();
    }

    public void write(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(refreshToken, ttl).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path(PATH)
                .maxAge(maxAge)
                .build();
    }
}
