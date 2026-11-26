package com.habitcrew.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, unique = true, length = 20)
    private String nickname;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** 체험 계정이면 사라지는 시각, 일반 회원이면 null */
    @Column(name = "guest_expires_at")
    private Instant guestExpiresAt;

    public User(String email, String passwordHash, String nickname, Instant now) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static User guest(String email, String passwordHash, String nickname, Instant now, Instant expiresAt) {
        User user = new User(email, passwordHash, nickname, now);
        user.guestExpiresAt = expiresAt;
        return user;
    }

    public boolean isGuest() {
        return guestExpiresAt != null;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
