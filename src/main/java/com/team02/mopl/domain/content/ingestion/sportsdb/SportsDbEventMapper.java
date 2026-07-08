package com.team02.mopl.domain.content.ingestion.sportsdb;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.ExternalContentMapper;
import com.team02.mopl.domain.content.ingestion.sportsdb.dto.SportsDbEventDto;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

// SportsDB 경기 응답을 ExternalContentData로 변환 (필수 값 검증, 길이 truncate, 태그 변환)
// TMDB 매퍼와 동일하게 Spring 빈이 아니라 실행 시점에 생성하는 값 객체로 설계함
@Slf4j
public class SportsDbEventMapper implements ExternalContentMapper<SportsDbEventDto> {

  // externalId 네임스페이스: 향후 리그/팀 등 다른 SportsDB 리소스 수집 시 id 충돌을 막기 위한 prefix
  // (TMDB의 "movie:"/"tv:" 분리와 동일한 규칙)
  static final String EXTERNAL_ID_PREFIX = "event:";

  // contents 테이블 컬럼 길이 제약
  private static final int TITLE_MAX_LENGTH = 100;
  private static final int DESCRIPTION_MAX_LENGTH = 255;
  private static final int TAG_NAME_MAX_LENGTH = 20;
  private static final String ELLIPSIS = "...";

  private static final String DEFAULT_DESCRIPTION = "경기 정보가 제공되지 않았습니다.";
  // 이미지도 기본 썸네일 설정도 없을 때 엔티티 NOT NULL 검증을 통과시키기 위한 최종 방어값
  private static final String FALLBACK_THUMBNAIL_URL = "https://placehold.co/500x750?text=No+Image";

  private final String defaultThumbnailUrl;

  public SportsDbEventMapper(String defaultThumbnailUrl) {
    this.defaultThumbnailUrl = defaultThumbnailUrl;
  }

  @Override
  public Optional<ExternalContentData> map(SportsDbEventDto raw) {
    if (!StringUtils.hasText(raw.idEvent())) {
      log.warn("SportsDB 응답에 유효한 idEvent가 없어 건너뜁니다. event={}", raw.strEvent());
      return Optional.empty();
    }
    if (!StringUtils.hasText(raw.strEvent())) {
      log.warn("SportsDB 응답에 경기명이 없어 건너뜁니다. idEvent={}", raw.idEvent());
      return Optional.empty();
    }

    return Optional.of(
        new ExternalContentData(
            ContentSource.SPORTS_DB,
            EXTERNAL_ID_PREFIX + raw.idEvent().strip(),
            ContentType.SPORT,
            truncate(raw.strEvent().strip(), TITLE_MAX_LENGTH),
            mapDescription(raw),
            mapThumbnailUrl(raw),
            mapTags(raw)));
  }

  // strDescriptionEN은 대부분 비어 있으므로 리그/시즌/경기장/일자를 합성해 대체한다
  private String mapDescription(SportsDbEventDto raw) {
    // 원본 설명이 있으면 strip하고 그대로 사용
    if (StringUtils.hasText(raw.strDescriptionEN())) {
      return truncate(raw.strDescriptionEN().strip(), DESCRIPTION_MAX_LENGTH);
    }
    String leagueSeason = // 리그 + 시즌 합성
        Stream.of(raw.strLeague(), raw.strSeason())
            .filter(StringUtils::hasText) // 빈 값 걸러내고
            .map(String::strip)
            .reduce((league, season) -> league + " " + season) // 공백 넣고 이어 붙이기
            .orElse(""); // 둘 다 없다면 빈 문자열
    String composed = // 리그시즌 + 경기장 + 날짜 합성
        Stream.of(leagueSeason, raw.strVenue(), raw.dateEvent())
            .filter(StringUtils::hasText)
            .map(String::strip)
            .reduce((left, right) -> left + " - " + right)
            .orElse("");
    if (composed.isEmpty()) {
      return DEFAULT_DESCRIPTION;
    }
    return truncate(composed, DESCRIPTION_MAX_LENGTH);
  }

  private String mapThumbnailUrl(SportsDbEventDto raw) {
    if (StringUtils.hasText(raw.strThumb())) {
      return raw.strThumb();
    }
    if (StringUtils.hasText(raw.strPoster())) {
      return raw.strPoster();
    }
    return StringUtils.hasText(defaultThumbnailUrl) ? defaultThumbnailUrl : FALLBACK_THUMBNAIL_URL;
  }

  // 종목/리그명을 태그로 변환. 리그명이 핵심 태그라 20자 초과 시 버리지 않고 truncate한다
  // TODO: 태그 길이 제한 수정 검토
  private List<String> mapTags(SportsDbEventDto raw) {
    return Stream.of(raw.strSport(), raw.strLeague())
        .filter(StringUtils::hasText)
        .map(name -> truncate(name.strip(), TAG_NAME_MAX_LENGTH))
        .distinct()
        .toList();
  }

  // DB VARCHAR 길이는 Code Point 기준이므로 Surrogate Pair를 깨지 않게 Code Point 단위로 자른다
  private static String truncate(String value, int maxLength) {
    if (value.codePointCount(0, value.length()) <= maxLength) {
      return value;
    }
    int end = value.offsetByCodePoints(0, maxLength - ELLIPSIS.length());
    return value.substring(0, end) + ELLIPSIS;
  }
}
