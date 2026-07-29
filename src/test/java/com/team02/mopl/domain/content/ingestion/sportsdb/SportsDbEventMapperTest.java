package com.team02.mopl.domain.content.ingestion.sportsdb;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.sportsdb.dto.SportsDbEventDto;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SportsDbEventMapperTest {

  private static final String DEFAULT_THUMBNAIL_URL = "https://cdn.example.com/default.png";

  private final SportsDbEventMapper mapper = new SportsDbEventMapper(DEFAULT_THUMBNAIL_URL);

  private static SportsDbEventDto event(
      String idEvent, String strEvent, String strThumb, String strPoster) {
    return new SportsDbEventDto(
        idEvent,
        strEvent,
        null,
        "Soccer",
        "English Premier League",
        "2025-2026",
        "Anfield",
        "2025-08-15",
        strThumb,
        strPoster);
  }

  @Test
  @DisplayName("정상 응답이면 source/externalId/type/제목/설명/썸네일/태그를 모두 매핑한다")
  void map_whenValidEvent_mapsAllFields() {
    // given
    SportsDbEventDto raw =
        event("2267073", "Liverpool vs Bournemouth", "https://img/thumb.jpg", null);

    // when
    ExternalContentData data = mapper.map(raw).orElseThrow();

    // then
    assertThat(data.source()).isEqualTo(ContentSource.SPORTS_DB);
    assertThat(data.externalId()).isEqualTo("event:2267073");
    assertThat(data.contentType()).isEqualTo(ContentType.SPORT);
    assertThat(data.title()).isEqualTo("Liverpool vs Bournemouth");
    assertThat(data.description())
        .isEqualTo("English Premier League 2025-2026 - Anfield - 2025-08-15");
    assertThat(data.thumbnailUrl()).isEqualTo("https://img/thumb.jpg");
    // "English Premier League"(22자)는 20 code point로 truncate된다
    assertThat(data.tags()).containsExactly("Soccer", "English Premier L...");
  }

  @Test
  @DisplayName("idEvent나 경기명이 없으면 건너뛴다 (Optional.empty)")
  void map_whenRequiredFieldMissing_returnsEmpty() {
    // given
    SportsDbEventDto noId = event(" ", "Liverpool vs Bournemouth", null, null);
    SportsDbEventDto noTitle = event("2267073", null, null, null);

    // when
    Optional<ExternalContentData> idResult = mapper.map(noId);
    Optional<ExternalContentData> titleResult = mapper.map(noTitle);

    // then
    assertThat(idResult).isEmpty();
    assertThat(titleResult).isEmpty();
  }

  @Test
  @DisplayName("strDescriptionEN이 있으면 합성하지 않고 그대로 사용하며 255자로 truncate한다")
  void map_whenDescriptionPresent_usesItWithTruncation() {
    // given
    String longDescription = "설명".repeat(200); // 400자
    SportsDbEventDto raw =
        new SportsDbEventDto(
            "1", "A vs B", longDescription, null, null, null, null, null, null, null);

    // when
    ExternalContentData data = mapper.map(raw).orElseThrow();

    // then
    assertThat(data.description().codePointCount(0, data.description().length())).isEqualTo(255);
    assertThat(data.description()).endsWith("...");
  }

  @Test
  @DisplayName("설명 합성 재료(리그/시즌/경기장/일자)가 전부 없으면 기본 문구를 사용한다")
  void map_whenNoDescriptionSources_usesDefaultDescription() {
    // given
    SportsDbEventDto raw =
        new SportsDbEventDto("1", "A vs B", null, null, null, null, null, null, null, null);

    // when
    ExternalContentData data = mapper.map(raw).orElseThrow();

    // then
    assertThat(data.description()).isEqualTo("경기 정보가 제공되지 않았습니다.");
  }

  @Test
  @DisplayName("썸네일은 strThumb, strPoster, 기본 썸네일 순으로 fallback한다")
  void map_thumbnailFallbackChain() {
    // given - strThumb 없음, strPoster 있음
    SportsDbEventDto posterOnly = event("1", "A vs B", " ", "https://img/poster.jpg");
    // given - 둘 다 없음
    SportsDbEventDto noImage = event("2", "C vs D", null, null);

    // when & then
    assertThat(mapper.map(posterOnly).orElseThrow().thumbnailUrl())
        .isEqualTo("https://img/poster.jpg");
    assertThat(mapper.map(noImage).orElseThrow().thumbnailUrl()).isEqualTo(DEFAULT_THUMBNAIL_URL);
  }

  @Test
  @DisplayName("기본 썸네일 설정도 없으면 최종 방어값(placehold)을 사용한다")
  void map_whenNoDefaultThumbnail_usesFallbackUrl() {
    // given
    SportsDbEventMapper noDefaultMapper = new SportsDbEventMapper("");
    SportsDbEventDto raw = event("1", "A vs B", null, null);

    // when
    ExternalContentData data = noDefaultMapper.map(raw).orElseThrow();

    // then
    assertThat(data.thumbnailUrl()).isEqualTo("https://placehold.co/500x750?text=No+Image");
  }

  @Test
  @DisplayName("태그는 blank를 제외하고 중복을 제거한다")
  void map_tagsFilterBlankAndDuplicate() {
    // given - 종목과 리그명이 동일한 경우 (중복 제거 확인)
    SportsDbEventDto raw =
        new SportsDbEventDto("1", "A vs B", null, "Soccer", "Soccer", null, null, null, null, null);
    SportsDbEventDto noTags =
        new SportsDbEventDto("2", "C vs D", null, " ", null, null, null, null, null, null);

    // when & then
    assertThat(mapper.map(raw).orElseThrow().tags()).containsExactly("Soccer");
    assertThat(mapper.map(noTags).orElseThrow().tags()).isEmpty();
  }

  @Test
  @DisplayName("100자를 넘는 경기명은 code point 기준으로 truncate한다")
  void map_whenTitleTooLong_truncates() {
    // given
    SportsDbEventDto raw = event("1", "경기".repeat(100), null, null); // 200자

    // when
    ExternalContentData data = mapper.map(raw).orElseThrow();

    // then
    assertThat(data.title().codePointCount(0, data.title().length())).isEqualTo(100);
    assertThat(data.title()).endsWith("...");
  }
}
