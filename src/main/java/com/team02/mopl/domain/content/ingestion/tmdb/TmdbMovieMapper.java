package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.tmdb.dto.TmdbMovieDto;
import java.util.Map;
import java.util.Optional;

// TMDB 영화 응답 1건을 정규화된 수집 데이터(MOVIE)로 변환
public class TmdbMovieMapper extends AbstractTmdbMapper<TmdbMovieDto> {

  // TMDB tv id와의 (source, external_id) 충돌 방지용 네임스페이스
  static final String EXTERNAL_ID_PREFIX = "movie:";

  public TmdbMovieMapper(
      Map<Integer, String> genreNames, String imageBaseUrl, String defaultThumbnailUrl) {
    super(genreNames, imageBaseUrl, defaultThumbnailUrl);
  }

  public TmdbMovieMapper(
      Map<Integer, String> genreNames,
      String imageBaseUrl,
      String defaultThumbnailUrl,
      boolean requireCompleteMedia) {
    super(genreNames, imageBaseUrl, defaultThumbnailUrl, requireCompleteMedia);
  }

  @Override
  public Optional<ExternalContentData> map(TmdbMovieDto raw) {
    return mapFields(
        raw.id(),
        EXTERNAL_ID_PREFIX,
        ContentType.MOVIE,
        raw.title(),
        raw.overview(),
        raw.posterPath(),
        raw.genreIds());
  }
}
