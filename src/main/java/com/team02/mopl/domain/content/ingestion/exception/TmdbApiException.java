package com.team02.mopl.domain.content.ingestion.exception;

import com.team02.mopl.global.exception.ErrorCode;
import java.net.URI;

// TMDB API 호출 실패 예외. 에러 응답 상태 코드와 요청 URI를 details로 보존
public class TmdbApiException extends ExternalApiException {

  public TmdbApiException(int statusCode, URI uri) {
    super(ErrorCode.EXTERNAL_API_ERROR);
    addDetail("statusCode", String.valueOf(statusCode));
    addDetail("uri", String.valueOf(uri));
  }

  public TmdbApiException(Throwable cause) {
    super(ErrorCode.EXTERNAL_API_ERROR, cause);
  }
}
