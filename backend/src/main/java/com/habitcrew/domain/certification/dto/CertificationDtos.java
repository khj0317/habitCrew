package com.habitcrew.domain.certification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class CertificationDtos {

    private CertificationDtos() {
    }

    public record Author(Long userId, String nickname) {
    }

    public record Item(
            Long id,
            Author author,
            LocalDate certDate,
            String message,
            String imageUrl,
            String thumbnailUrl,
            int imageWidth,
            int imageHeight,
            int likeCount,
            boolean likedByMe,
            int commentCount,
            boolean mine,
            Instant createdAt,
            // 신고로 가려진 사진: 크루원에게는 사진 주소를 주지 않는다 (작성자와 크루장은 판단을 위해 볼 수 있다)
            boolean hidden,
            boolean reportedByMe
    ) {
    }

    /** 피드 한 페이지. nextCursor가 null이면 마지막 페이지 */
    public record Feed(List<Item> items, Long nextCursor) {
    }

    public record MemberStatus(Long userId, String nickname, boolean certified, Long certificationId) {
    }

    /** 오늘의 인증 현황 */
    public record Today(
            LocalDate date,
            boolean certDay,
            Instant deadline,
            Long myCertificationId,
            int certifiedCount,
            int memberCount,
            List<MemberStatus> members
    ) {
    }

    public record LikeResult(boolean liked, int likeCount) {
    }

    public enum ReportReason { SPAM, INAPPROPRIATE, UNRELATED, OTHER }

    public record ReportRequest(@jakarta.validation.constraints.NotNull(message = "신고 이유를 골라 주세요.") ReportReason reason) {
    }

    public record HiddenRequest(@jakarta.validation.constraints.NotNull Boolean hidden) {
    }

    /** 신고·가리기 결과: 지금 가려졌는지 */
    public record HiddenResult(boolean hidden) {
    }

    public record Comment(Long id, Author author, String content, boolean mine, Instant createdAt) {
    }

    public record CommentRequest(
            @NotBlank(message = "댓글을 입력해 주세요.")
            @Size(max = 300, message = "댓글은 300자까지 쓸 수 있어요.")
            String content
    ) {
        public CommentRequest {
            content = content == null ? null : content.strip();
        }
    }
}
