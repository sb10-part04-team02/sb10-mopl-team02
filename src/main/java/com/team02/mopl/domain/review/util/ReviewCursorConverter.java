package com.team02.mopl.domain.review.util;

import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.global.exception.InvalidCursorException;
import java.time.Instant;
import java.time.format.DateTimeParseException;

// 커서 문자열을 실제 정렬 키 값으로 바꾸거나(toSortKey), 반대로 만들어주는(toCursor) 변환기.
// 정렬 기준마다 타입이 다르므로(createdAt=Instant, rating=Double) 서비스 계층에서 변환·검증한다.
public final class ReviewCursorConverter {

  private ReviewCursorConverter() {} // 인스턴스 생성 금지

  // 커서 문자열 -> 정렬 키 값
  public static Comparable<?> toSortKey(ReviewSortBy sortBy, String cursor) {
    if (cursor == null || cursor.isBlank()) {
      return null;
    }
    try {
      return switch (sortBy) {
        case CREATED_AT -> Instant.parse(cursor);
        case RATING -> parseRating(cursor);
      };
    } catch (DateTimeParseException | NumberFormatException e) {
      throw new InvalidCursorException(sortBy.name(), cursor, e);
    }
  }

  // rating 커서: NaN·Infinity는 (rating, id) 비교에서 무의미하므로 차단한다
  private static Double parseRating(String cursor) {
    double value = Double.parseDouble(cursor);
    if (!Double.isFinite(value)) {
      throw new NumberFormatException("rating 커서는 유한한 숫자여야 합니다: " + cursor);
    }
    return value;
  }

  // 마지막 행의 정렬 키 값 -> 다음 커서 문자열
  public static String toCursor(ReviewSortBy sortBy, Review last) {
    return switch (sortBy) {
      case CREATED_AT -> last.getCreatedAt().toString();
      case RATING -> Double.toString(last.getRating());
    };
  }
}
