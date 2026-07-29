package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// TMDB /tv/popular, /discover/tv 응답의 드라마 1건. 영화와 달리 제목 필드가 name
// https://developer.themoviedb.org/reference/tv-series-popular-list
// firstAirDate는 discover backfill의 방영일 워터마크 계산에만 쓰이고 Content로는 저장되지 않는다.
public record TmdbTvDto(
    long id,
    String name, // title
    String overview,
    @JsonProperty("poster_path") String posterPath,
    @JsonProperty("first_air_date") String firstAirDate,
    @JsonProperty("genre_ids") List<Integer> genreIds) {

  public TmdbTvDto {
    genreIds = genreIds == null ? List.of() : List.copyOf(genreIds);
  }
}
