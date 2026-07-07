package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TmdbTvMapperTest {

  private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

  private final TmdbTvMapper mapper =
      new TmdbTvMapper(Map.of(10765, "SF"), IMAGE_BASE_URL, "https://cdn.example.com/default.png");

  @Test
  @DisplayName("정상 응답은 name을 제목으로 하는 TV_SERIES 타입의 수집 데이터로 매핑된다")
  void map_success() {
    TmdbTvDto raw = new TmdbTvDto(1399, "왕좌의 게임", "줄거리", "/poster.jpg", List.of(10765));

    ExternalContentData data = mapper.map(raw).orElseThrow();

    assertThat(data.source()).isEqualTo(ContentSource.TMDB);
    assertThat(data.externalId()).isEqualTo("1399");
    assertThat(data.contentType()).isEqualTo(ContentType.TV_SERIES);
    assertThat(data.title()).isEqualTo("왕좌의 게임");
    assertThat(data.thumbnailUrl()).isEqualTo(IMAGE_BASE_URL + "/poster.jpg");
    assertThat(data.tags()).containsExactly("SF");
  }

  @Test
  @DisplayName("name이 없으면 건너뛴다")
  void map_whenBlankName_returnsEmpty() {
    assertThat(mapper.map(new TmdbTvDto(1399, null, "줄거리", null, List.of()))).isEmpty();
  }
}
