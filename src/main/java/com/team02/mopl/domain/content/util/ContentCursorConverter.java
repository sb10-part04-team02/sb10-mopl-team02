package com.team02.mopl.domain.content.util;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.content.exception.InvalidCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

// sortBy 별 커서 문자열 <-> 정렬 키 값 변환. toSortKey 와 toCursor 의 타입을 한 곳에서 짝지어 보장한다.
// 어디까지 읽었는지 기록하는 커서 문자열을 실제 정렬 키 값으로 바꾸거나(toSortKey), 반대로 만들어주는(toCursor) 변환기

// 정렬 기준마다 타입이 다르다.
// API는 커서를 무조건 문자열로 주고 받아야 하는데, DB 쿼리할 때는 각 타입으로 비교해야 한다.
// 그래서 그 사이에 변환을 담당하는 유틸이 필요

// Util 클래스
public final class ContentCursorConverter {

  private ContentCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값
  public static Comparable<?> toSortKey(SortBy sortBy, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return switch (sortBy) {
        case CREATED_AT -> Instant.parse(cursor);
        case RATE -> Double.valueOf(cursor);
        case WATCHER_COUNT -> Long.valueOf(cursor);
      };
    } catch (DateTimeParseException | NumberFormatException e) {
      throw new InvalidCursorException(sortBy, cursor);
      // DateTimeParseException: 문자열을 날짜나 시간 객체로 변환(파싱)할 때 형식이 맞지 않아 발생하는 에러
      // NumberFormatException: 숫자가 아닌 문자열을 숫자로 변환하려고 할 때 발생하는 예외
    }
  }

  // 마지막 행의 정렬 키 값 -> 다음 커서 문자열
  public static String toCursor(SortBy sortBy, Content last, long watcherCount) {
    return switch (sortBy) {
      case CREATED_AT -> last.getCreatedAt().toString();
      case RATE -> Double.toString(last.getAverageRating());
      case WATCHER_COUNT -> Long.toString(watcherCount);
    };
  }
}
