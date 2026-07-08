package com.team02.mopl.domain.dm.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.util.StringToEnumConverterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

class DirectMessageSortByTest {

  private final Converter<String, DirectMessageSortBy> converter =
      new StringToEnumConverterFactory().getConverter(DirectMessageSortBy.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void 프론트엔드_camelCase_요청값이_enum으로_변환된다() {
    assertThat(converter.convert("createdAt")).isEqualTo(DirectMessageSortBy.CREATED_AT);
  }

  @Test
  void 기존_대문자_스네이크_요청값도_하위호환된다() {
    assertThat(converter.convert("CREATED_AT")).isEqualTo(DirectMessageSortBy.CREATED_AT);
  }

  @Test
  void 응답_직렬화시_camelCase로_반환된다() throws Exception {
    assertThat(objectMapper.writeValueAsString(DirectMessageSortBy.CREATED_AT))
        .isEqualTo("\"createdAt\"");
  }
}
