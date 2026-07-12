package com.team02.mopl.global.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

  // Common
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 요청입니다."),
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "COMMON_401", "인증이 필요합니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "COMMON_403", "접근 권한이 없습니다."),
  NOT_FOUND(HttpStatus.NOT_FOUND, "COMMON_404", "요청한 리소스를 찾을 수 없습니다."),
  METHOD_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "COMMON_400", "지원하지 않는 HTTP 메서드입니다."),
  CONFLICT(HttpStatus.BAD_REQUEST, "COMMON_400", "이미 존재하거나 충돌이 발생한 리소스입니다."),
  INVALID_CURSOR(HttpStatus.BAD_REQUEST, "COMMON_400", "유효하지 않은 커서 값입니다."),
  INVALID_CURSOR_REQUEST(HttpStatus.BAD_REQUEST, "COMMON_400", "커서 페이지네이션 요청 파라미터가 올바르지 않습니다."),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "COMMON_500", "서버 내부 오류가 발생했습니다."),
  INVALID_ENUM_VALUE(HttpStatus.BAD_REQUEST, "COMMON_400", "잘못된 enum 값입니다."),

  // Auth
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401", "만료되었거나 유효하지 않은 토큰입니다."),
  COMPROMISED_TOKEN(HttpStatus.FORBIDDEN, "AUTH_403", "보안 위협이 감지되어 접속이 차단되었습니다."),

  // User
  USER_LOCKED(HttpStatus.UNAUTHORIZED, "USER_401", "잠금처리된 유저입니다. 어드민에게 문의하세요."),
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404", "사용자를 찾을 수 없습니다."),
  EMAIL_DUPLICATED(HttpStatus.CONFLICT, "USER_409", "이미 존재하는 이메일입니다."),

  // Content
  CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CONTENT_404", "콘텐츠를 찾을 수 없습니다."),

  // Playlist
  PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PLAYLIST_404", "플레이리스트를 찾을 수 없습니다."),
  PLAYLIST_FORBIDDEN(HttpStatus.FORBIDDEN, "PLAYLIST_403", "해당 플레이리스트에 접근할 권한이 없습니다."),
  PLAYLIST_CONTENT_ALREADY_EXISTS(
      HttpStatus.BAD_REQUEST, "PLAYLIST_CONTENT_400", "이미 플레이리스트에 추가된 콘텐츠입니다."),
  PLAYLIST_CONTENT_NOT_FOUND(
      HttpStatus.NOT_FOUND, "PLAYLIST_CONTENT_404", "플레이리스트에서 콘텐츠를 찾을 수 없습니다."),
  SUBSCRIPTION_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "SUBSCRIPTION_400", "이미 구독한 플레이리스트입니다."),
  SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "SUBSCRIPTION_404", "구독 정보를 찾을 수 없습니다."),
  CANNOT_SUBSCRIBE_OWN_PLAYLIST(
      HttpStatus.BAD_REQUEST, "SUBSCRIPTION_400", "본인의 플레이리스트는 구독할 수 없습니다."),

  // Review
  REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "REVIEW_404", "리뷰를 찾을 수 없습니다."),
  REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "REVIEW_409", "이미 작성한 리뷰가 존재합니다."),

  // Conversation
  CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "CONVERSATION_404", "대화를 찾을 수 없습니다."),
  SELF_CONVERSATION(HttpStatus.BAD_REQUEST, "CONVERSATION_400", "자기 자신과 대화방을 만들 수 없습니다."),
  CONVERSATION_ALREADY_EXISTS(HttpStatus.CONFLICT, "CONVERSATION_409", "이미 존재하는 대화방입니다."),
  DIRECT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "DIRECT_MESSAGE_404", "다이렉트 메시지를 찾을 수 없습니다."),

  // Watch Room
  WATCH_ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "WATCH_ROOM_404", "시청방을 찾을 수 없습니다."),

  // Follow
  FOLLOW_NOT_FOUND(HttpStatus.NOT_FOUND, "FOLLOW_404", "팔로우 정보를 찾을 수 없습니다."),
  FOLLOW_FORBIDDEN(HttpStatus.FORBIDDEN, "FOLLOW_403", "해당 팔로우에 접근할 권한이 없습니다."),
  FOLLOW_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "FOLLOW_400", "이미 팔로우한 사용자입니다."),
  CANNOT_FOLLOW_SELF(HttpStatus.BAD_REQUEST, "FOLLOW_400", "자기 자신은 팔로우할 수 없습니다."),

  // Notification
  NOTIFICATION_FORBIDDEN(HttpStatus.FORBIDDEN, "NOTIFICATION_403", "해당 알림에 접근할 권한이 없습니다."),
  NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_404", "알림을 찾을 수 없습니다."),

  // External (콘텐츠 수집)
  EXTERNAL_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "EXTERNAL_500", "외부 API 호출에 실패했습니다.");

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
