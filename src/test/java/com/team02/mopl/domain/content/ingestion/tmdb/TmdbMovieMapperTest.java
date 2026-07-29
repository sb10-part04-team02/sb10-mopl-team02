package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TmdbMovieMapperTest {

  private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
  private static final String DEFAULT_THUMBNAIL_URL = "https://cdn.example.com/default.png";
  private static final Map<Integer, String> GENRES = Map.of(28, "액션", 35, "코미디");

  private final TmdbMovieMapper mapper =
      new TmdbMovieMapper(GENRES, IMAGE_BASE_URL, DEFAULT_THUMBNAIL_URL);

  @Test
  @DisplayName("정상 응답은 MOVIE 타입의 수집 데이터로 매핑된다")
  void map_success() {
    // given
    TmdbMovieDto raw = new TmdbMovieDto(550, "파이트 클럽", "줄거리", "/poster.jpg", null, List.of(28, 35));

    // when
    ExternalContentData data = mapper.map(raw).orElseThrow();

    // then
    assertThat(data.source()).isEqualTo(ContentSource.TMDB);
    assertThat(data.externalId()).isEqualTo("movie:550");
    assertThat(data.contentType()).isEqualTo(ContentType.MOVIE);
    assertThat(data.title()).isEqualTo("파이트 클럽");
    assertThat(data.description()).isEqualTo("줄거리");
    assertThat(data.thumbnailUrl()).isEqualTo(IMAGE_BASE_URL + "/poster.jpg");
    assertThat(data.tags()).containsExactly("액션", "코미디");
  }

  @Test
  @DisplayName("id가 0 이하면 건너뛴다")
  void map_whenInvalidId_returnsEmpty() {
    // given
    TmdbMovieDto raw = new TmdbMovieDto(0, "제목", "줄거리", "/poster.jpg", null, List.of());

    // when & then
    assertThat(mapper.map(raw)).isEmpty();
  }

  @Test
  @DisplayName("제목이 없거나 공백이면 건너뛴다")
  void map_whenBlankTitle_returnsEmpty() {
    assertThat(mapper.map(new TmdbMovieDto(550, null, "줄거리", null, null, List.of()))).isEmpty();
    assertThat(mapper.map(new TmdbMovieDto(550, "  ", "줄거리", null, null, List.of()))).isEmpty();
  }

  @Test
  @DisplayName("제목이 100자를 넘으면 말줄임표를 붙여 100자로 자른다")
  void map_whenTitleTooLong_truncates() {
    // given
    String longTitle = "가".repeat(150);

    // when
    ExternalContentData data =
        mapper.map(new TmdbMovieDto(550, longTitle, "줄거리", null, null, List.of())).orElseThrow();

    // then
    assertThat(data.title()).hasSize(100).endsWith("...").startsWith("가".repeat(97));
  }

  @Test
  @DisplayName("overview가 없으면 기본 문구로 대체한다")
  void map_whenBlankOverview_usesDefaultDescription() {
    // when
    ExternalContentData data =
        mapper.map(new TmdbMovieDto(550, "제목", " ", null, null, List.of())).orElseThrow();

    // then
    assertThat(data.description()).isEqualTo("줄거리 정보가 제공되지 않았습니다.");
  }

  @Test
  @DisplayName("overview가 255자를 넘으면 말줄임표를 붙여 255자로 자르되 서로게이트 페어를 깨지 않는다")
  void map_whenOverviewTooLong_truncatesOnCodePointBoundary() {
    // given - U+1F600(😀)은 UTF-16에서 2 char인 서로게이트 페어
    String longOverview = "😀".repeat(300);

    // when
    ExternalContentData data =
        mapper.map(new TmdbMovieDto(550, "제목", longOverview, null, null, List.of())).orElseThrow();

    // then
    assertThat(data.description().codePointCount(0, data.description().length())).isEqualTo(255);
    assertThat(data.description()).endsWith("...").startsWith("😀".repeat(252));
  }

  @Test
  @DisplayName("poster_path가 없으면 기본 썸네일 URL을 사용한다")
  void map_whenNoPoster_usesDefaultThumbnail() {
    // when
    ExternalContentData data =
        mapper.map(new TmdbMovieDto(550, "제목", "줄거리", null, null, List.of())).orElseThrow();

    // then
    assertThat(data.thumbnailUrl()).isEqualTo(DEFAULT_THUMBNAIL_URL);
  }

  @Test
  @DisplayName("기본 썸네일 설정마저 비어 있으면 최종 방어 placeholder URL을 사용한다")
  void map_whenNoPosterAndNoDefault_usesFallbackThumbnail() {
    // given
    TmdbMovieMapper noDefaultMapper = new TmdbMovieMapper(GENRES, IMAGE_BASE_URL, "");

    // when
    ExternalContentData data =
        noDefaultMapper
            .map(new TmdbMovieDto(550, "제목", "줄거리", null, null, List.of()))
            .orElseThrow();

    // then
    assertThat(data.thumbnailUrl()).isNotBlank();
  }

  @Test
  @DisplayName("requireCompleteMedia면 poster_path가 없을 때 폴백 대신 건너뛴다")
  void map_whenStrictAndNoPoster_returnsEmpty() {
    // given
    TmdbMovieMapper strictMapper =
        new TmdbMovieMapper(GENRES, IMAGE_BASE_URL, DEFAULT_THUMBNAIL_URL, true);

    // when & then
    assertThat(strictMapper.map(new TmdbMovieDto(550, "제목", "줄거리", null, null, List.of())))
        .isEmpty();
    assertThat(strictMapper.map(new TmdbMovieDto(550, "제목", "줄거리", " ", null, List.of())))
        .isEmpty();
  }

  @Test
  @DisplayName("requireCompleteMedia면 overview가 없을 때 기본 문구 대신 건너뛴다")
  void map_whenStrictAndNoOverview_returnsEmpty() {
    // given
    TmdbMovieMapper strictMapper =
        new TmdbMovieMapper(GENRES, IMAGE_BASE_URL, DEFAULT_THUMBNAIL_URL, true);

    // when & then
    assertThat(strictMapper.map(new TmdbMovieDto(550, "제목", null, "/poster.jpg", null, List.of())))
        .isEmpty();
    assertThat(strictMapper.map(new TmdbMovieDto(550, "제목", " ", "/poster.jpg", null, List.of())))
        .isEmpty();
  }

  @Test
  @DisplayName("requireCompleteMedia여도 poster_path와 overview가 모두 있으면 정상 매핑된다")
  void map_whenStrictAndComplete_maps() {
    // given
    TmdbMovieMapper strictMapper =
        new TmdbMovieMapper(GENRES, IMAGE_BASE_URL, DEFAULT_THUMBNAIL_URL, true);

    // when
    ExternalContentData data =
        strictMapper
            .map(new TmdbMovieDto(550, "제목", "줄거리", "/poster.jpg", null, List.of()))
            .orElseThrow();

    // then
    assertThat(data.thumbnailUrl()).isEqualTo(IMAGE_BASE_URL + "/poster.jpg");
    assertThat(data.description()).isEqualTo("줄거리");
  }

  @Test
  @DisplayName("장르 맵에 없는 id는 태그에서 제외하고 중복 이름은 하나만 남긴다")
  void map_filtersUnknownAndDuplicateGenres() {
    // when
    ExternalContentData data =
        mapper
            .map(new TmdbMovieDto(550, "제목", "줄거리", null, null, List.of(28, 28, 99999)))
            .orElseThrow();

    // then
    assertThat(data.tags()).containsExactly("액션");
  }

  @Test
  @DisplayName("20자를 넘는 장르 이름은 태그에서 제외한다")
  void map_filtersTooLongGenreNames() {
    // given
    TmdbMovieMapper longGenreMapper =
        new TmdbMovieMapper(
            Map.of(1, "가".repeat(21), 2, "액션"), IMAGE_BASE_URL, DEFAULT_THUMBNAIL_URL);

    // when
    Optional<ExternalContentData> data =
        longGenreMapper.map(new TmdbMovieDto(550, "제목", "줄거리", null, null, List.of(1, 2)));

    // then
    assertThat(data.orElseThrow().tags()).containsExactly("액션");
  }
}
