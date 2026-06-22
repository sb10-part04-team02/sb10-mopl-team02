package com.team02.mopl.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "COMMON_405", "지원하지 않는 HTTP 메서드입니다."),
    CONFLICT(HttpStatus.CONFLICT, "COMMON_409", "이미 존재하거나 충돌이 발생한 리소스입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
    CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_404", "콘텐츠를 찾을 수 없습니다."),
    PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYLIST_404", "플레이리스트를 찾을 수 없습니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_404", "리뷰를 찾을 수 없습니다."),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONVERSATION_404", "대화를 찾을 수 없습니다."),
    WATCH_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "WATCH_ROOM_404", "시청방을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus status, String code, String message) {
        this.status = status;
        this.code = code;
        this.message = message;
    }
    public int getStatusValue() {
        return status.value();
    }


}