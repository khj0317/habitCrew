package com.habitcrew.domain.user;

import java.time.Instant;

/**
 * guestExpiresAt: 체험 계정이면 사라지는 시각, 일반 회원이면 null
 * notifyActivity: 내 인증에 댓글·좋아요가 달리면 알림 받기
 */
public record UserResponse(Long id, String email, String nickname, Instant createdAt, boolean guest,
                           Instant guestExpiresAt, boolean notifyActivity) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.isGuest() ? null : user.getEmail(), user.getNickname(),
                user.getCreatedAt(), user.isGuest(), user.getGuestExpiresAt(), user.isNotifyActivity());
    }
}
