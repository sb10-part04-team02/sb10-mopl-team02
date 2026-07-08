package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.ExternalContentMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

// TMDB 영화/드라마 매퍼의 공통 변환 규칙(필수 값 검증, 길이 truncate, 장르 태그 변환)
// 장르 id-이름 Map이 수집 실행마다 달라지므로 Spring 빈이 아니라 실행 시점에 생성하는 값 객체로 설계함
// TMDB API에서 받아온 영화/드라마 원시 응답을, 우리 서비스의 ExternalContentData로 변환하는 로직 중 공통 부분 모아둔 추상 클래스
@Slf4j
abstract class AbstractTmdbMapper<T> implements ExternalContentMapper<T> {

  // contents 테이블 컬럼 길이 제약
  private static final int TITLE_MAX_LENGTH = 100;
  private static final int DESCRIPTION_MAX_LENGTH = 255; // TODO: VARCHAR or TEXT 수정
  private static final int TAG_NAME_MAX_LENGTH = 20; // TODO: 태그 종류 파악 후 길이 조정
  private static final String ELLIPSIS = "...";

  private static final String DEFAULT_DESCRIPTION = "줄거리 정보가 제공되지 않았습니다.";
  // 포스터도 기본 썸네일 설정도 없을 때 엔티티 NOT NULL 검증을 통과시키기 위한 최종 방어값
  private static final String FALLBACK_THUMBNAIL_URL = "https://placehold.co/500x750?text=No+Image";

  private final Map<Integer, String> genreNames;
  private final String imageBaseUrl;
  private final String defaultThumbnailUrl;

  protected AbstractTmdbMapper(
      Map<Integer, String> genreNames, String imageBaseUrl, String defaultThumbnailUrl) {
    this.genreNames = Map.copyOf(genreNames);
    this.imageBaseUrl = imageBaseUrl;
    this.defaultThumbnailUrl = defaultThumbnailUrl;
  }

  // externalIdPrefix: TMDB의 movie id와 tv id는 서로 독립된 시퀀스라 같은 숫자가 다른 작품을 가리킬 수 있음
  // (source, external_id) 유니크 키가 충돌하지 않도록 "movie:"/"tv:" prefix로 분리
  protected Optional<ExternalContentData> mapFields(
      long id,
      String externalIdPrefix,
      ContentType contentType,
      String title,
      String overview,
      String posterPath,
      List<Integer> genreIds) {
    if (id <= 0) {
      log.warn("TMDB 응답에 유효한 id가 없어 건너뜁니다. type={}, title={}", contentType, title);
      return Optional.empty();
    }
    if (!StringUtils.hasText(title)) {
      log.warn("TMDB 응답에 제목이 없어 건너뜁니다. type={}, id={}", contentType, id);
      return Optional.empty();
    }

    return Optional.of(
        new ExternalContentData(
            ContentSource.TMDB,
            externalIdPrefix + id,
            contentType,
            truncate(title.strip(), TITLE_MAX_LENGTH),
            mapDescription(overview),
            mapThumbnailUrl(posterPath),
            mapTags(genreIds)));
  }

  private String mapDescription(String overview) {
    if (!StringUtils.hasText(overview)) { // overview가 비어있는 경우 - 사용자에게 보여줄 기본 문구로 대체
      return DEFAULT_DESCRIPTION;
    }
    return truncate(overview.strip(), DESCRIPTION_MAX_LENGTH); // 255자 제한
  }

  private String mapThumbnailUrl(String posterPath) {
    if (StringUtils.hasText(posterPath)) {
      return imageBaseUrl + posterPath; // 정상 사진 URL 조립
    }
    return StringUtils.hasText(defaultThumbnailUrl) ? defaultThumbnailUrl : FALLBACK_THUMBNAIL_URL;
  }

  // TMDB 장르 id 리스트를 생성자에서 주입받은 genreNames 맵으로 이름 리스트로 변환
  private List<String> mapTags(List<Integer> genreIds) {
    return genreIds.stream()
        .map(genreNames::get)
        .filter(name -> name != null && !name.isBlank() && name.length() <= TAG_NAME_MAX_LENGTH)
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
