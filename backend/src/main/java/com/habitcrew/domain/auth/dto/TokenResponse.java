package com.habitcrew.domain.auth.dto;

import com.habitcrew.domain.user.UserResponse;

/**
 * 리프레시 토큰은 HttpOnly 쿠키로만 내려주고 본문에는 넣지 않는다 (JavaScript가 읽을 수 없게).
 */
public record TokenResponse(String accessToken, long expiresIn, UserResponse user) {
}
