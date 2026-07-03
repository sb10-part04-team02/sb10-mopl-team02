package com.team02.mopl.domain.playlist.util;

import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.domain.playlist.exception.PlaylistInvalidCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

// 커서 문자열을 실제 정렬 키 값으로 바꾸거나(toSortKey), 반대로 만들어주는(toCursor) 변환기.
// 정렬 기준마다 타입이 다르므로(updatedAt=Instant, subscribeCount=Long) 서비스 계층에서 변환·검증한다.
public final class PlaylistCursorConverter {

  private PlaylistCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값
  public static Comparable<?> toSortKey(PlaylistSortBy sortBy, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return switch (sortBy) {
        case UPDATED_AT -> Instant.parse(cursor);
        case SUBSCRIBE_COUNT -> Long.valueOf(cursor);
      };
    } catch (DateTimeParseException | NumberFormatException e) {
      throw new PlaylistInvalidCursorException(sortBy, cursor);
    }
  }

  // 마지막 행의 정렬 키 값 -> 다음 커서 문자열
  public static String toCursor(PlaylistSortBy sortBy, Playlist last) {
    return switch (sortBy) {
      case UPDATED_AT -> last.getUpdatedAt().toString();
      case SUBSCRIBE_COUNT -> Long.toString(last.getSubscriberCount());
    };
  }
}
