package com.habitcrew.domain.certification;

import com.habitcrew.domain.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "certifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Certification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "crew_id", nullable = false)
    private Long crewId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "cert_date", nullable = false)
    private LocalDate certDate;

    @Column(length = 200)
    private String message;

    @Column(name = "image_key", nullable = false, length = 200)
    private String imageKey;

    @Column(name = "thumbnail_key", nullable = false, length = 200)
    private String thumbnailKey;

    @Column(name = "image_width", nullable = false)
    private int imageWidth;

    @Column(name = "image_height", nullable = false)
    private int imageHeight;

    /** 좋아요·댓글 수는 동시에 바뀔 수 있어서 엔티티로 고치지 않고 리포지토리의 원자적 UPDATE로만 바꾼다 */
    @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false, insertable = false, updatable = false)
    private int commentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 신고가 쌓이거나 크루장이 가린 사진. 기록·정산에는 그대로 남고 사진만 가린다 */
    @Column(nullable = false, insertable = false, updatable = false)
    private boolean hidden;

    public Certification(Long crewId, User user, LocalDate certDate, String message, String imageKey,
                         String thumbnailKey, int imageWidth, int imageHeight, Instant now) {
        this.crewId = crewId;
        this.user = user;
        this.certDate = certDate;
        this.message = message;
        this.imageKey = imageKey;
        this.thumbnailKey = thumbnailKey;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.createdAt = now;
    }

    public boolean isAuthor(Long userId) {
        return user.getId().equals(userId);
    }
}
