package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// TMDB 영화의 국가별 개봉 정보 응답. 영화의 국가별 관람 등급(certification)은 목록 API에 없고 여기에만 담긴다
// https://developer.themoviedb.org/reference/movie-release-dates
public record TmdbReleaseDatesResponse(long id, List<Result> results) {

  public TmdbReleaseDatesResponse {
    results = results == null ? List.of() : List.copyOf(results);
  }

  // 국가 1곳의 개봉 정보. 같은 국가라도 개봉 형태(극장/디지털 등)별로 여러 건이 올 수 있다
  public record Result(
      @JsonProperty("iso_3166_1") String country, // 전 세계 국가와 부속 영토의 이름을 나타내는 표준 코드
      @JsonProperty("release_dates") List<ReleaseDate> releaseDates) {

    public Result {
      releaseDates = releaseDates == null ? List.of() : List.copyOf(releaseDates);
    }
  }

  // certification은 등급 미등록 시 빈 문자열로 내려온다
  public record ReleaseDate(String certification) {}
}
