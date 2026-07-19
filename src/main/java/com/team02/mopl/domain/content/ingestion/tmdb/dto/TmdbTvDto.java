package com.team02.mopl.domain.content.ingestion.tmdb.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

// TMDB /tv/popular 응답의 드라마 1건. 영화와 달리 제목 필드가 name
// https://developer.themoviedb.org/reference/tv-series-popular-list
// adult는 TMDB 기준 하드코어 포르노 여부로, 한국 19세 등급과는 별개다(등급은 content_ratings에서 확인)
public record TmdbTvDto(
    long id,
    String name, // title
    String overview,
    @JsonProperty("poster_path") String posterPath,
    @JsonProperty("genre_ids") List<Integer> genreIds,
    boolean adult) {

  public TmdbTvDto {
    genreIds = genreIds == null ? List.of() : List.copyOf(genreIds);
  }

  // adult 없이 생성하는 기존 호출부용. 응답에 adult가 없으면 성인물이 아닌 것으로 본다
  public TmdbTvDto(
      long id, String name, String overview, String posterPath, List<Integer> genreIds) {
    this(id, name, overview, posterPath, genreIds, false);
  }
}
