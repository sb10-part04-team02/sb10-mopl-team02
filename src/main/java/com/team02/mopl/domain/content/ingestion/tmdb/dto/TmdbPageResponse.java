package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** TMDB 목록 API의 공통 페이지 응답. */
public record TmdbPageResponse<T>(
    int page, List<T> results, @JsonProperty("total_pages") int totalPages) {

  public TmdbPageResponse {
    results = results == null ? List.of() : List.copyOf(results);
  }
}
