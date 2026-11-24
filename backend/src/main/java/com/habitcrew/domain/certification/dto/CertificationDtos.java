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
            Instant createdAt
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
