package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// TMDB /movie/popular, /discover/movie 응답의 영화 1건
// https://developer.themoviedb.org/reference/movie-popular-list
// releaseDate는 discover backfill의 개봉일 워터마크 계산에만 쓰이고 Content로는 저장되지 않는다.
public record TmdbMovieDto(
    long id,
    String title,
    String overview,
    @JsonProperty("poster_path") String posterPath,
    @JsonProperty("release_date") String releaseDate,
    @JsonProperty("genre_ids") List<Integer> genreIds) {

  public TmdbMovieDto {
    genreIds = genreIds == null ? List.of() : List.copyOf(genreIds);
  }
}
