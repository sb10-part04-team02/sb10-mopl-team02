package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** TMDB /movie/popular 응답의 영화 1건. */
public record TmdbMovieDto(
    long id,
    String title,
    String overview,
    @JsonProperty("poster_path") String posterPath,
    @JsonProperty("genre_ids") List<Integer> genreIds) {

  public TmdbMovieDto {
    genreIds = genreIds == null ? List.of() : List.copyOf(genreIds);
  }
}
