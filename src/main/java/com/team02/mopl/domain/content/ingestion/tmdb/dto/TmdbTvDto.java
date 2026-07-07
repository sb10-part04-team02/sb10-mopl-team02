package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** TMDB /tv/popular 응답의 드라마 1건. 영화와 달리 제목 필드가 name이다. */
public record TmdbTvDto(
    long id,
    String name,
    String overview,
    @JsonProperty("poster_path") String posterPath,
    @JsonProperty("genre_ids") List<Integer> genreIds) {

  public TmdbTvDto {
    genreIds = genreIds == null ? List.of() : List.copyOf(genreIds);
  }
}
