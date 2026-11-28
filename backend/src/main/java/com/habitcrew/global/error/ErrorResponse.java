package com.habitcrew.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.slf4j.MDC;

import java.util.List;

/** 공통 에러 응답. requestId로 서버 로그에서 이 요청을 찾을 수 있다 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(int status, String code, String message, List<FieldError> errors, String requestId) {

    public record FieldError(String field, String reason) {
    }

    public ErrorResponse(int status, String code, String message, List<FieldError> errors) {
        this(status, code, message, errors, MDC.get("requestId"));
    }

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), message, List.of());
    }

    public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
        return new ErrorResponse(errorCode.getStatus().value(), errorCode.name(), errorCode.getMessage(), errors);
    }
}
