package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import java.util.List;

// TMDB /genre/{movie|tv}/list 응답
// https://developer.themoviedb.org/reference/genre-movie-list
// https://developer.themoviedb.org/reference/genre-tv-list
public record TmdbGenreListResponse(List<TmdbGenreDto> genres) {

  public TmdbGenreListResponse {
    genres = genres == null ? List.of() : List.copyOf(genres);
  }
}
