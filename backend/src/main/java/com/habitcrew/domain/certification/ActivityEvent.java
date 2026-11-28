package com.habitcrew.domain.certification;

/**
 * 내 인증에 다른 크루원이 반응했다 (좋아요·댓글). 커밋된 뒤에 알림으로 보낸다.
 *
 * @param commentPreview 댓글일 때 내용 앞부분, 좋아요면 null
 */
public record ActivityEvent(Type type, Long crewId, Long certificationId, Long authorId, Long actorId,
                            String commentPreview) {

    public enum Type { LIKE, COMMENT }
}
