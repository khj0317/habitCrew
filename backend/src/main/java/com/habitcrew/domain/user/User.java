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

    /** 탈퇴한 시각. 탈퇴하면 이메일·닉네임·비밀번호를 지워 다시 로그인할 수 없게 한다 */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    /** 내 인증에 댓글·좋아요가 달리면 알림 받기 */
    @Column(name = "notify_activity", nullable = false)
    private boolean notifyActivity = true;

    public boolean isGuest() {
        return guestExpiresAt != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void changeNickname(String nickname) {
        this.nickname = nickname;
    }

    public void changeNotifyActivity(boolean notifyActivity) {
        this.notifyActivity = notifyActivity;
    }

    /** 탈퇴: 개인정보를 지우고 같은 이메일로 다시 가입할 수 있게 비운다. 행은 지난 벌금 기록이 가리키므로 남긴다 */
    public void anonymize(String unusablePasswordHash, Instant now) {
        this.email = "deleted-" + id + "@deleted.habitcrew.local";
        this.nickname = "탈퇴한사용자_" + id;
        this.passwordHash = unusablePasswordHash;
        this.notifyActivity = false;
        this.deletedAt = now;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
