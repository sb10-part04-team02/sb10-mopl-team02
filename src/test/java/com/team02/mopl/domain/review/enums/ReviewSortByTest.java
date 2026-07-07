package com.team02.mopl.domain.review.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.util.StringToEnumConverterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

class ReviewSortByTest {

  private final Converter<String, ReviewSortBy> converter =
      new StringToEnumConverterFactory().getConverter(ReviewSortBy.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void 프론트엔드_camelCase_요청값이_enum으로_변환된다() {
    assertThat(converter.convert("createdAt")).isEqualTo(ReviewSortBy.CREATED_AT);
    assertThat(converter.convert("rating")).isEqualTo(ReviewSortBy.RATING);
  }

  @Test
  void 응답_직렬화시_camelCase로_반환된다() throws Exception {
    assertThat(objectMapper.writeValueAsString(ReviewSortBy.CREATED_AT)).isEqualTo("\"createdAt\"");
    assertThat(objectMapper.writeValueAsString(ReviewSortBy.RATING)).isEqualTo("\"rating\"");
  }
}
