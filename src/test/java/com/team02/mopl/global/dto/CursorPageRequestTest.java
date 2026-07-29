package com.team02.mopl.global.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.global.enums.SortDirection;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("CursorPageRequest 단위 테스트")
class CursorPageRequestTest {

  @Nested
  @DisplayName("normalizeLimit - 페이지 크기 보정")
  class NormalizeLimit {

    @Test
    @DisplayName("limit이 null(미지정)이면 기본값 20을 반환한다")
    void returnsDefault_whenLimitNull() {
      // when & then
      assertThat(CursorPageRequest.normalizeLimit(null)).isEqualTo(20);
    }

    @ParameterizedTest
    @CsvSource({
      "-5, 1", // 음수 -> 하한 1로 클램프
      "0, 1", // 0 -> 하한 1로 클램프
      "1, 1", // 하한 경계
      "20, 20", // 기본 페이지 크기
      "100, 100", // 상한 경계
      "150, 100" // 상한 초과 -> 100으로 클램프
    })
    @DisplayName("보낸 값은 1 ~ 100 범위로 클램프한다")
    void clampsToRange(int input, int expected) {
      // when & then
      assertThat(CursorPageRequest.normalizeLimit(input)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("normalizeSortDirection - 정렬 방향 기본값")
  class NormalizeSortDirection {

    @Test
    @DisplayName("null이면 DESCENDING을 기본값으로 반환한다")
    void returnsDescending_whenNull() {
      // when & then
      assertThat(CursorPageRequest.normalizeSortDirection(null))
          .isEqualTo(SortDirection.DESCENDING);
    }

    @Test
    @DisplayName("지정된 방향은 그대로 반환한다")
    void returnsGivenDirection() {
      // when & then
      assertThat(CursorPageRequest.normalizeSortDirection(SortDirection.ASCENDING))
          .isEqualTo(SortDirection.ASCENDING);
      assertThat(CursorPageRequest.normalizeSortDirection(SortDirection.DESCENDING))
          .isEqualTo(SortDirection.DESCENDING);
    }
  }

  @Nested
  @DisplayName("isValidCursorCombo - cursor/idAfter 동반 검증")
  class IsValidCursorCombo {

    @Test
    @DisplayName("둘 다 없으면(첫 페이지) 유효하다")
    void valid_whenBothAbsent() {
      // when & then
      assertThat(CursorPageRequest.isValidCursorCombo(null, null)).isTrue();
      // 공백 커서는 없는 것으로 취급
      assertThat(CursorPageRequest.isValidCursorCombo("  ", null)).isTrue();
    }

    @Test
    @DisplayName("둘 다 있으면(다음 페이지) 유효하다")
    void valid_whenBothPresent() {
      // when & then
      assertThat(CursorPageRequest.isValidCursorCombo("cursor", UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("cursor만 있으면 유효하지 않다")
    void invalid_whenCursorOnly() {
      // when & then
      assertThat(CursorPageRequest.isValidCursorCombo("cursor", null)).isFalse();
    }

    @Test
    @DisplayName("idAfter만 있으면 유효하지 않다")
    void invalid_whenIdAfterOnly() {
      // when & then
      assertThat(CursorPageRequest.isValidCursorCombo(null, UUID.randomUUID())).isFalse();
      assertThat(CursorPageRequest.isValidCursorCombo("  ", UUID.randomUUID())).isFalse();
    }
  }
}
