package com.habitcrew.domain.crew;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@Table(name = "crews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Crew {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String name;

    @Column(length = 200)
    private String description;

    @Column(name = "cert_days", nullable = false)
    private int certDaysMask;

    @Column(name = "penalty_points", nullable = false)
    private int penaltyPoints;

    @Column(name = "max_members", nullable = false)
    private int maxMembers;

    @Column(name = "invite_code", nullable = false, unique = true, length = 8)
    private String inviteCode;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Crew(String name, String description, Collection<DayOfWeek> certDays, int penaltyPoints,
                int maxMembers, String inviteCode, Long ownerId, Instant now) {
        this.name = name;
        this.description = description;
        this.certDaysMask = CertDays.toMask(certDays);
        this.penaltyPoints = penaltyPoints;
        this.maxMembers = maxMembers;
        this.inviteCode = inviteCode;
        this.ownerId = ownerId;
        this.createdAt = now;
    }

    public List<DayOfWeek> getCertDays() {
        return CertDays.fromMask(certDaysMask);
    }

    public boolean isOwner(Long userId) {
        return ownerId.equals(userId);
    }

    /** 인증 요일·벌금은 바꾸지 않는다: 정산 도중 규칙이 바뀌면 크루원끼리 분쟁이 생기므로 만들 때 정한 규칙을 지킨다 */
    public void update(String name, String description, int maxMembers) {
        this.name = name;
        this.description = description;
        this.maxMembers = maxMembers;
    }

    public void changeInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }

    public void changeOwner(Long ownerId) {
        this.ownerId = ownerId;
    }
}
