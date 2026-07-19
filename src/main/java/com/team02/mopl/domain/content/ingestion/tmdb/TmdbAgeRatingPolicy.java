package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.exception.ExternalApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbContentRatingsResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbReleaseDatesResponse;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// 한국 19세(청소년 관람불가) 콘텐츠를 수집에서 제외하기 위한 등급 판정
// 판정 순서: KR 등급이 있으면 KR로 판정 -> 없으면 US 등급으로 폴백 -> 둘 다 없으면 통과
// KR 등급은 사용자 기여 데이터라 누락이 잦은 반면 US 등급은 커버리지가 좋아 폴백으로만 쓴다
// popular 목록 응답에는 등급이 없어 항목마다 추가 호출이 발생한다
@Slf4j
@Component
public class TmdbAgeRatingPolicy {

  private static final String KR = "KR";
  private static final String US = "US";

  // KR은 숫자 코드로 내려온다(release_dates/content_ratings API로 확인험: 청소년 관람불가 = "19", 15세 = "15").
  // '제한상영가'는 숫자 코드가 없는 별도 범주라 문자열이 미확인이나, 실제 제한 대상이므로 방어적으로 포함한다.
  private static final Set<String> KR_RESTRICTED = Set.of("19", "제한상영가");
  // US에는 19세 등급이 없다. R은 성인 전용이 아니라(보호자 동반 관람 가능) 제외 대상에서 뺀다
  private static final Set<String> US_RESTRICTED_MOVIE = Set.of("NC-17");
  private static final Set<String> US_RESTRICTED_TV = Set.of("TV-MA");

  private final TmdbClient tmdbClient;

  public TmdbAgeRatingPolicy(TmdbClient tmdbClient) {
    this.tmdbClient = tmdbClient;
  }

  private static boolean restricted(
      ContentType contentType,
      long id,
      String country,
      List<String> certifications,
      Set<String> restrictedCertifications) {
    Optional<String> hit =
        certifications.stream()
            .filter(certification -> restrictedCertifications.contains(normalize(certification)))
            .findFirst();
    hit.ifPresent(
        certification ->
            log.warn(
                "TMDB 성인 등급 콘텐츠라 건너뜁니다. type={}, id={}, country={}, certification={}",
                contentType,
                id,
                country,
                certification));
    return hit.isPresent();
  }

  // 영화는 같은 국가라도 개봉 형태별로 등급이 여러 건 올 수 있어 전부 본다
  private static List<String> certifications(TmdbReleaseDatesResponse response, String country) {
    return response.results().stream()
        .filter(result -> country.equals(result.country()))
        .flatMap(result -> result.releaseDates().stream())
        .map(TmdbReleaseDatesResponse.ReleaseDate::certification)
        .filter(StringUtils::hasText) // 등급 미등록은 빈 문자열로 온다
        .toList();
  }

  private static List<String> ratings(TmdbContentRatingsResponse response, String country) {
    return response.results().stream()
        .filter(result -> country.equals(result.country()))
        .map(TmdbContentRatingsResponse.Result::rating)
        .filter(StringUtils::hasText)
        .toList();
  }

  // 공백(\s)를 모두 제거
  private static String normalize(String certification) {
    return certification.replaceAll("\\s", "");
  }

  public boolean isRestrictedMovie(long movieId) {
    try {
      TmdbReleaseDatesResponse response = tmdbClient.fetchMovieReleaseDates(movieId);
      List<String> krCertifications = certifications(response, KR);
      if (!krCertifications.isEmpty()) {
        return restricted(ContentType.MOVIE, movieId, KR, krCertifications, KR_RESTRICTED);
      }
      return restricted(
          ContentType.MOVIE, movieId, US, certifications(response, US), US_RESTRICTED_MOVIE);
    } catch (ExternalApiException e) {
      log.warn("TMDB 영화 등급 조회 실패로 등급 확인 없이 수집합니다. id={}", movieId, e);
      return false;
    }
  }

  public boolean isRestrictedTv(long seriesId) {
    try {
      TmdbContentRatingsResponse response = tmdbClient.fetchTvContentRatings(seriesId);
      List<String> krRatings = ratings(response, KR);
      if (!krRatings.isEmpty()) {
        return restricted(ContentType.TV_SERIES, seriesId, KR, krRatings, KR_RESTRICTED);
      }
      return restricted(
          ContentType.TV_SERIES, seriesId, US, ratings(response, US), US_RESTRICTED_TV);
    } catch (ExternalApiException e) {
      log.warn("TMDB 드라마 등급 조회 실패로 등급 확인 없이 수집합니다. id={}", seriesId, e);
      return false;
    }
  }
}
