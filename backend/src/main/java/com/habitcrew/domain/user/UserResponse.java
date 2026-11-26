package com.habitcrew.domain.user;

import java.time.Instant;

/** guestExpiresAt: 체험 계정이면 사라지는 시각, 일반 회원이면 null */
public record UserResponse(Long id, String email, String nickname, Instant createdAt, boolean guest,
                           Instant guestExpiresAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.isGuest() ? null : user.getEmail(), user.getNickname(),
                user.getCreatedAt(), user.isGuest(), user.getGuestExpiresAt());
    }
}
