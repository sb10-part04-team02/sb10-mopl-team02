package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbTvDto;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 세부 규칙은 TmdbMovieMapper와 공통 로직을 공유하므로 최소 검증
class TmdbTvMapperTest {

  private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";

  private final TmdbTvMapper mapper =
      new TmdbTvMapper(Map.of(10765, "SF"), IMAGE_BASE_URL, "https://cdn.example.com/default.png");

  @Test
  @DisplayName("정상 응답은 name을 제목으로 하는 TV_SERIES 타입의 수집 데이터로 매핑된다")
  void map_success() {
    // given
    TmdbTvDto raw = new TmdbTvDto(1399, "왕좌의 게임", "줄거리", "/poster.jpg", List.of(10765));

    // when
    ExternalContentData data = mapper.map(raw).orElseThrow();

    // then
    assertThat(data.source()).isEqualTo(ContentSource.TMDB);
    assertThat(data.externalId()).isEqualTo("tv:1399");
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

  @Test
  @DisplayName("adult가 true면 건너뛴다")
  void map_whenAdult_returnsEmpty() {
    assertThat(mapper.map(new TmdbTvDto(1399, "제목", "줄거리", "/poster.jpg", List.of(), true)))
        .isEmpty();
  }

  @Test
  @DisplayName("영화와 같은 숫자 id라도 externalId가 충돌하지 않는다 (movie:/tv: 네임스페이스 분리)")
  void map_whenSameNumericIdAsMovie_producesDistinctExternalId() {
    // given - TMDB의 movie id와 tv id는 독립 시퀀스라 같은 숫자가 다른 작품일 수 있다
    TmdbMovieMapper movieMapper =
        new TmdbMovieMapper(Map.of(), IMAGE_BASE_URL, "https://cdn.example.com/default.png");

    // when
    String movieExternalId =
        movieMapper
            .map(new TmdbMovieDto(1396, "영화", "줄거리", null, List.of()))
            .orElseThrow()
            .externalId();
    String tvExternalId =
        mapper.map(new TmdbTvDto(1396, "드라마", "줄거리", null, List.of())).orElseThrow().externalId();

    // then
    assertThat(movieExternalId).isEqualTo("movie:1396");
    assertThat(tvExternalId).isEqualTo("tv:1396");
    assertThat(movieExternalId).isNotEqualTo(tvExternalId);
  }
}
