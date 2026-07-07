package com.team02.mopl.domain.playlist.enums;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.global.util.StringToEnumConverterFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;

class PlaylistSortByTest {

  private final Converter<String, PlaylistSortBy> converter =
      new StringToEnumConverterFactory().getConverter(PlaylistSortBy.class);
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void 프론트엔드_camelCase_요청값이_enum으로_변환된다() {
    assertThat(converter.convert("updatedAt")).isEqualTo(PlaylistSortBy.UPDATED_AT);
    assertThat(converter.convert("subscribeCount")).isEqualTo(PlaylistSortBy.SUBSCRIBE_COUNT);
  }

  @Test
  void 응답_직렬화시_camelCase로_반환된다() throws Exception {
    assertThat(objectMapper.writeValueAsString(PlaylistSortBy.UPDATED_AT))
        .isEqualTo("\"updatedAt\"");
    assertThat(objectMapper.writeValueAsString(PlaylistSortBy.SUBSCRIBE_COUNT))
        .isEqualTo("\"subscribeCount\"");
  }
}
