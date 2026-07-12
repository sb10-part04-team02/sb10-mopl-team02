package com.team02.mopl.domain.content.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.content.exception.InvalidCursorException;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("ContentCursorConverter 단위 테스트")
class ContentCursorConverterTest {

  private Content contentWith(Instant createdAt, double averageRating) {
    Content content = new Content(ContentType.MOVIE, "제목", "설명", "url");
    ReflectionTestUtils.setField(content, "createdAt", createdAt);
    ReflectionTestUtils.setField(content, "averageRating", averageRating);
    return content;
  }

  @Nested
  @DisplayName("toSortKey - 커서 문자열을 정렬 키 값으로 변환")
  class ToSortKey {

    @ParameterizedTest
    @EnumSource(SortBy.class)
    @DisplayName("커서가 null이거나 공백이면 정렬 기준과 무관하게 null을 반환한다(첫 페이지)")
    void returnsNull_whenCursorNullOrBlank(SortBy sortBy) {
      // when & then
      assertThat(ContentCursorConverter.toSortKey(sortBy, null)).isNull();
      assertThat(ContentCursorConverter.toSortKey(sortBy, "")).isNull();
      assertThat(ContentCursorConverter.toSortKey(sortBy, "   ")).isNull();
    }

    @Test
    @DisplayName("CREATED_AT 커서는 ISO-8601 문자열을 Instant로 파싱한다")
    void parsesInstant_whenCreatedAt() {
      // when
      Comparable<?> result =
          ContentCursorConverter.toSortKey(SortBy.CREATED_AT, "2026-06-30T10:00:00Z");

      // then
      assertThat(result)
          .isInstanceOf(Instant.class)
          .isEqualTo(Instant.parse("2026-06-30T10:00:00Z"));
    }

    @Test
    @DisplayName("RATE 커서는 Double로 파싱한다")
    void parsesDouble_whenRate() {
      // when
      Comparable<?> result = ContentCursorConverter.toSortKey(SortBy.RATE, "4.5");

      // then
      assertThat(result).isInstanceOf(Double.class).isEqualTo(4.5);
    }

    @Test
    @DisplayName("WATCHER_COUNT 커서는 Long으로 파싱한다")
    void parsesLong_whenWatcherCount() {
      // when
      Comparable<?> result = ContentCursorConverter.toSortKey(SortBy.WATCHER_COUNT, "1250");

      // then
      assertThat(result).isInstanceOf(Long.class).isEqualTo(1250L);
    }

    @Test
    @DisplayName("CREATED_AT 커서 형식이 잘못되면 InvalidCursorException(INVALID_CURSOR, 400)을 던진다")
    void throwsInvalidCursor_whenCreatedAtUnparseable() {
      // when & then
      assertThatThrownBy(() -> ContentCursorConverter.toSortKey(SortBy.CREATED_AT, "not-a-date"))
          .isInstanceOf(InvalidCursorException.class)
          .extracting(e -> ((BusinessException) e).getErrorCode())
          .isEqualTo(ErrorCode.INVALID_CURSOR);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.0.0"})
    @DisplayName("RATE 커서가 숫자가 아니면 InvalidCursorException을 던진다")
    void throwsInvalidCursor_whenRateNotNumber(String cursor) {
      // when & then
      assertThatThrownBy(() -> ContentCursorConverter.toSortKey(SortBy.RATE, cursor))
          .isInstanceOf(InvalidCursorException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "3.14"})
    @DisplayName("WATCHER_COUNT 커서가 정수가 아니면 InvalidCursorException을 던진다")
    void throwsInvalidCursor_whenWatcherCountNotLong(String cursor) {
      // when & then
      assertThatThrownBy(() -> ContentCursorConverter.toSortKey(SortBy.WATCHER_COUNT, cursor))
          .isInstanceOf(InvalidCursorException.class);
    }

    @Test
    @DisplayName("InvalidCursorException은 details에 cursor 키로 정렬 기준이 포함된 사유를 담는다")
    void exceptionContainsCursorDetail() {
      // when
      InvalidCursorException ex =
          assertThrows(
              InvalidCursorException.class,
              () -> ContentCursorConverter.toSortKey(SortBy.RATE, "abc"));

      // then
      assertThat(ex.getDetails()).containsKey("cursor");
      assertThat(ex.getDetails().get("cursor")).contains("RATE");
    }
  }

  @Nested
  @DisplayName("toCursor - 마지막 행의 정렬 키 값을 다음 커서 문자열로 변환")
  class ToCursor {

    @Test
    @DisplayName("CREATED_AT은 createdAt의 ISO-8601 문자열을 반환한다")
    void returnsCreatedAtString() {
      // given
      Instant createdAt = Instant.parse("2026-06-30T10:00:00Z");
      Content last = contentWith(createdAt, 0.0);

      // when & then
      assertThat(ContentCursorConverter.toCursor(SortBy.CREATED_AT, last, 99L))
          .isEqualTo(createdAt.toString());
    }

    @Test
    @DisplayName("RATE는 averageRating의 문자열을 반환한다")
    void returnsAverageRatingString() {
      // given
      Content last = contentWith(Instant.now(), 4.5);

      // when & then
      assertThat(ContentCursorConverter.toCursor(SortBy.RATE, last, 99L)).isEqualTo("4.5");
    }

    @Test
    @DisplayName("WATCHER_COUNT는 인자로 받은 watcherCount의 문자열을 반환한다(엔티티 값 무시)")
    void returnsWatcherCountArgString() {
      // given
      Content last = contentWith(Instant.now(), 4.5);

      // when & then
      assertThat(ContentCursorConverter.toCursor(SortBy.WATCHER_COUNT, last, 1250L))
          .isEqualTo("1250");
    }
  }
}
