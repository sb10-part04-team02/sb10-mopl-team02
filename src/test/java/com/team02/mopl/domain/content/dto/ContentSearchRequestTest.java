package com.team02.mopl.domain.content.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("ContentSearchRequest 단위 테스트")
class ContentSearchRequestTest {

  private ContentSearchRequest requestWithLimit(int limit) {
    return new ContentSearchRequest(null, null, null, null, null, limit, null, null);
  }

  @ParameterizedTest
  @CsvSource({
    "-1, 20", // 음수 -> 기본값 20
    "0, 20", // 0(미지정) -> 기본값 20
    "1, 1", // 하한 경계
    "20, 20", // 기본 페이지 크기
    "100, 100", // 상한 경계
    "150, 100" // 상한 초과 -> 100으로 보정
  })
  @DisplayName("normalizedLimit는 0 이하면 20, 100 초과면 100으로 보정한다")
  void normalizedLimit_clampsToBounds(int input, int expected) {
    // given
    ContentSearchRequest request = requestWithLimit(input);

    // when & then
    assertThat(request.normalizedLimit()).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"-1, 21", "0, 21", "1, 2", "20, 21", "100, 101", "150, 101"})
  @DisplayName("fetchLimit는 항상 normalizedLimit + 1이다(다음 페이지 판정용 여분 1건)")
  void fetchLimit_isNormalizedLimitPlusOne(int input, int expected) {
    // given
    ContentSearchRequest request = requestWithLimit(input);

    // when & then
    assertThat(request.fetchLimit()).isEqualTo(expected);
  }
}
