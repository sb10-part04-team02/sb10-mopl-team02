package com.team02.mopl.domain.content.ingestion.tmdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.team02.mopl.domain.content.ingestion.exception.TmdbApiException;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbContentRatingsResponse;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbReleaseDatesResponse;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TmdbAgeRatingPolicyTest {

  // 둘다 청소년관람불가 등급
  private static final long MOVIE_ID = 550L; // Fight Club
  private static final long TV_ID = 1399L; // Game of Thrones

  @Mock private TmdbClient tmdbClient;
  @InjectMocks private TmdbAgeRatingPolicy policy;

  private static TmdbReleaseDatesResponse.Result country(String code, String... certifications) {
    return new TmdbReleaseDatesResponse.Result(
        code,
        Arrays.stream(certifications).map(TmdbReleaseDatesResponse.ReleaseDate::new).toList());
  }

  private void givenMovieReleaseDates(TmdbReleaseDatesResponse.Result... results) {
    given(tmdbClient.fetchMovieReleaseDates(MOVIE_ID))
        .willReturn(new TmdbReleaseDatesResponse(MOVIE_ID, List.of(results)));
  }

  private void givenTvRatings(TmdbContentRatingsResponse.Result... results) {
    given(tmdbClient.fetchTvContentRatings(TV_ID))
        .willReturn(new TmdbContentRatingsResponse(TV_ID, List.of(results)));
  }

  @Test
  @DisplayName("KR 등급이 19면 제한 대상이다")
  void isRestrictedMovie_whenKrIs19_returnsTrue() {
    givenMovieReleaseDates(country("KR", "19"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isTrue();
  }

  @Test
  @DisplayName("KR 제한상영가는 공백이 섞여 들어와도 정규화해 제한 대상으로 판정한다")
  void isRestrictedMovie_whenKrIsRestrictedScreening_returnsTrue() {
    givenMovieReleaseDates(country("KR", "제한 상영가"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isTrue();
  }

  @Test
  @DisplayName("KR 등급이 15면 통과시킨다")
  void isRestrictedMovie_whenKrIs15_returnsFalse() {
    givenMovieReleaseDates(country("KR", "15"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isFalse();
  }

  @Test
  @DisplayName("같은 국가에 등급이 여러 건이면 하나라도 제한 등급일 때 제한 대상이다")
  void isRestrictedMovie_whenAnyKrCertificationIsRestricted_returnsTrue() {
    givenMovieReleaseDates(country("KR", "15", "19"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isTrue();
  }

  @Test
  @DisplayName("KR 등급이 있으면 US 등급은 보지 않는다 (KR 15 + US NC-17이면 통과)")
  void isRestrictedMovie_whenKrExists_ignoresUs() {
    givenMovieReleaseDates(country("KR", "15"), country("US", "NC-17"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isFalse();
  }

  @Test
  @DisplayName("KR 등급이 없으면 US NC-17로 폴백 판정한다")
  void isRestrictedMovie_whenNoKrAndUsIsNc17_returnsTrue() {
    givenMovieReleaseDates(country("US", "NC-17"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isTrue();
  }

  @Test
  @DisplayName("US R은 성인 전용이 아니므로 폴백 판정에서도 통과시킨다")
  void isRestrictedMovie_whenNoKrAndUsIsR_returnsFalse() {
    givenMovieReleaseDates(country("US", "R"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isFalse();
  }

  @Test
  @DisplayName("KR 등급이 빈 문자열이면 없는 것으로 보고 US로 폴백한다")
  void isRestrictedMovie_whenKrCertificationIsBlank_fallsBackToUs() {
    givenMovieReleaseDates(country("KR", ""), country("US", "NC-17"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isTrue();
  }

  @Test
  @DisplayName("KR/US 등급이 모두 없으면 통과시킨다")
  void isRestrictedMovie_whenNoCertification_returnsFalse() {
    givenMovieReleaseDates(country("JP", "R18+"));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isFalse();
  }

  @Test
  @DisplayName("등급 조회가 실패하면 수집을 막지 않고 통과시킨다")
  void isRestrictedMovie_whenFetchFails_returnsFalse() {
    given(tmdbClient.fetchMovieReleaseDates(MOVIE_ID))
        .willThrow(new TmdbApiException(new RuntimeException("일시 오류")));

    assertThat(policy.isRestrictedMovie(MOVIE_ID)).isFalse();
  }

  @Test
  @DisplayName("드라마도 KR 등급이 19면 제한 대상이다")
  void isRestrictedTv_whenKrIs19_returnsTrue() {
    givenTvRatings(new TmdbContentRatingsResponse.Result("KR", "19"));

    assertThat(policy.isRestrictedTv(TV_ID)).isTrue();
  }

  @Test
  @DisplayName("드라마 KR 등급이 15면 통과시킨다")
  void isRestrictedTv_whenKrIs15_returnsFalse() {
    givenTvRatings(new TmdbContentRatingsResponse.Result("KR", "15"));

    assertThat(policy.isRestrictedTv(TV_ID)).isFalse();
  }

  @Test
  @DisplayName("드라마는 KR 등급이 없으면 US TV-MA로 폴백 판정한다")
  void isRestrictedTv_whenNoKrAndUsIsTvMa_returnsTrue() {
    givenTvRatings(new TmdbContentRatingsResponse.Result("US", "TV-MA"));

    assertThat(policy.isRestrictedTv(TV_ID)).isTrue();
  }

  @Test
  @DisplayName("드라마 US TV-14는 통과시킨다")
  void isRestrictedTv_whenNoKrAndUsIsTv14_returnsFalse() {
    givenTvRatings(new TmdbContentRatingsResponse.Result("US", "TV-14"));

    assertThat(policy.isRestrictedTv(TV_ID)).isFalse();
  }

  @Test
  @DisplayName("드라마 등급 조회가 실패하면 통과시킨다")
  void isRestrictedTv_whenFetchFails_returnsFalse() {
    given(tmdbClient.fetchTvContentRatings(TV_ID))
        .willThrow(new TmdbApiException(new RuntimeException("일시 오류")));

    assertThat(policy.isRestrictedTv(TV_ID)).isFalse();
  }
}
