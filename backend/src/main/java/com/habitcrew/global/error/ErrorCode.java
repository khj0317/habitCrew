package com.habitcrew.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),
    CONFLICT(HttpStatus.CONFLICT, "요청이 현재 상태와 충돌합니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 잦아요. 잠시 후 다시 시도해 주세요."),

    // 인증
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않거나 만료된 토큰입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    LOGIN_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "로그인 시도가 너무 많아요. 잠시 후 다시 시도해 주세요."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "로그인이 만료되었어요. 다시 로그인해 주세요."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // 회원
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, "이미 가입된 이메일입니다."),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),

    // 크루
    CREW_NOT_FOUND(HttpStatus.NOT_FOUND, "크루를 찾을 수 없어요."),
    INVALID_INVITE_CODE(HttpStatus.NOT_FOUND, "초대 코드가 올바르지 않아요. 크루장에게 코드를 다시 확인해 주세요."),
    ALREADY_JOINED(HttpStatus.CONFLICT, "이미 참여한 크루예요."),
    CREW_FULL(HttpStatus.CONFLICT, "크루 정원이 가득 찼어요."),
    TOO_MANY_CREWS(HttpStatus.CONFLICT, "크루는 최대 10개까지 참여할 수 있어요."),
    NOT_CREW_OWNER(HttpStatus.FORBIDDEN, "크루장만 할 수 있어요."),
    OWNER_CANNOT_LEAVE(HttpStatus.CONFLICT, "크루장은 다른 크루원에게 크루장을 넘긴 뒤에 나갈 수 있어요."),
    CANNOT_TARGET_SELF(HttpStatus.BAD_REQUEST, "자기 자신에게는 할 수 없어요."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "크루원을 찾을 수 없어요."),
    MAX_MEMBERS_BELOW_CURRENT(HttpStatus.BAD_REQUEST, "정원은 지금 인원보다 적게 줄일 수 없어요."),

    // 인증
    PHOTO_REQUIRED(HttpStatus.BAD_REQUEST, "인증 사진을 올려 주세요."),
    UNSUPPORTED_IMAGE(HttpStatus.BAD_REQUEST, "JPG 또는 PNG 사진만 올릴 수 있어요."),
    IMAGE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "사진이 너무 커요. 10MB 이하로 올려 주세요."),
    IMAGE_DIMENSIONS_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진 해상도가 너무 커요."),
    IMAGE_TOO_SMALL(HttpStatus.BAD_REQUEST, "사진이 너무 작아요. 64픽셀 이상인 사진을 올려 주세요."),
    NOT_CERT_DAY(HttpStatus.BAD_REQUEST, "오늘은 이 크루의 인증일이 아니에요."),
    ALREADY_CERTIFIED(HttpStatus.CONFLICT, "오늘은 이미 인증했어요."),
    CERTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "인증을 찾을 수 없어요."),
    NOT_CERTIFICATION_AUTHOR(HttpStatus.FORBIDDEN, "내 인증만 지울 수 있어요."),
    PAST_CERTIFICATION(HttpStatus.CONFLICT, "지난 날짜의 인증은 지울 수 없어요. 이미 정산에 반영됐어요."),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "댓글을 찾을 수 없어요."),
    NOT_COMMENT_AUTHOR(HttpStatus.FORBIDDEN, "내 댓글만 지울 수 있어요."),
    CANNOT_REPORT_OWN(HttpStatus.BAD_REQUEST, "내 인증은 신고할 수 없어요."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "이미 신고한 인증이에요."),

    // 계정
    RESERVED_NICKNAME(HttpStatus.BAD_REQUEST, "'체험러'·'탈퇴한사용자'로 시작하는 닉네임은 쓸 수 없어요."),
    INVALID_CURRENT_PASSWORD(HttpStatus.BAD_REQUEST, "현재 비밀번호가 올바르지 않아요."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "링크가 만료됐거나 이미 사용한 링크예요. 비밀번호 찾기를 다시 해 주세요."),
    OWNER_OF_ACTIVE_CREWS(HttpStatus.CONFLICT, "크루원이 남아 있는 크루의 크루장이에요. 크루장을 넘긴 뒤에 탈퇴할 수 있어요."),

    // 체험 계정
    GUEST_RESTRICTED(HttpStatus.FORBIDDEN, "체험 계정에서는 다른 크루에 참여할 수 없어요. 회원가입 후 이용해 주세요."),
    GUEST_ACCOUNT_READONLY(HttpStatus.FORBIDDEN, "체험 계정은 계정 정보를 바꿀 수 없어요. 24시간 뒤 자동으로 사라져요."),
    GUEST_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "체험 계정을 너무 많이 만들었어요. 잠시 후 다시 시도해 주세요."),

    // 알림
    INVALID_PUSH_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "알림 구독 정보가 올바르지 않아요."),
    PUSH_NOT_SUBSCRIBED(HttpStatus.BAD_REQUEST, "먼저 이 기기에서 알림을 켜 주세요.");

    private final HttpStatus status;
    private final String message;
}
