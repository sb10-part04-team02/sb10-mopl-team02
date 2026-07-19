package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// TMDB 드라마의 국가별 관람 등급 응답. 영화의 release_dates에 대응하는 TV 쪽 엔드포인트
// https://developer.themoviedb.org/reference/tv-series-content-ratings
public record TmdbContentRatingsResponse(long id, List<Result> results) {

  public TmdbContentRatingsResponse {
    results = results == null ? List.of() : List.copyOf(results);
  }

  // 국가 1곳의 등급. 영화와 달리 국가당 등급이 하나다
  public record Result(@JsonProperty("iso_3166_1") String country, String rating) {}
}
