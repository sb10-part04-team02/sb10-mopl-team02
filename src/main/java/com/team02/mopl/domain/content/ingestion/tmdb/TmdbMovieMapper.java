package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import java.util.Map;
import java.util.Optional;

// TMDB 영화 응답 1건을 정규화된 수집 데이터(MOVIE)로 변환
public class TmdbMovieMapper extends AbstractTmdbMapper<TmdbMovieDto> {

  public TmdbMovieMapper(
      Map<Integer, String> genreNames, String imageBaseUrl, String defaultThumbnailUrl) {
    super(genreNames, imageBaseUrl, defaultThumbnailUrl);
  }

  @Override
  public Optional<ExternalContentData> map(TmdbMovieDto raw) {
    return mapFields(
        raw.id(), ContentType.MOVIE, raw.title(), raw.overview(), raw.posterPath(), raw.genreIds());
  }
}
