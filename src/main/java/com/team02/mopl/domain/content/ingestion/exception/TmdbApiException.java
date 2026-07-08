package com.team02.mopl.domain.content.ingestion.exception;

import com.team02.mopl.global.exception.ErrorCode;
import java.net.URI;

// TMDB API 호출 실패 예외. 에러 응답 상태 코드와 요청 URI를 details로 보존
// retryable: 일시적 장애(5xx, IO/타임아웃) 여부로, 호출 측 재시도 판단에 사용
public class TmdbApiException extends ExternalApiException {

  private final boolean retryable;

  public TmdbApiException(int statusCode, URI uri) {
    super(ErrorCode.EXTERNAL_API_ERROR);
    this.retryable = statusCode >= 500; // 5xx만 일시적 장애로 간주(4xx는 재시도 무의미)
    addDetail("statusCode", String.valueOf(statusCode));
    addDetail("uri", String.valueOf(uri));
  }

  public TmdbApiException(Throwable cause) {
    super(ErrorCode.EXTERNAL_API_ERROR, cause);
    this.retryable = true; // IO/타임아웃 등은 일시적 장애로 간주
  }

  public boolean isRetryable() {
    return retryable;
  }
}
