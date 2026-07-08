package com.team02.mopl.domain.content.ingestion.exception;

import com.team02.mopl.global.exception.ErrorCode;

// TMDB API 호출 실패 예외. 에러 응답 상태 코드를 details로 보존
// details는 ErrorResponse로 클라이언트에 노출되므로 요청 URI 등 내부 정보는 담지 않는다
// retryable: 일시적 장애(5xx, 429, IO/타임아웃) 여부로, 호출 측 재시도 판단에 사용
public class TmdbApiException extends ExternalApiException {

  private final boolean retryable;

  public TmdbApiException(int statusCode) {
    super(ErrorCode.EXTERNAL_API_ERROR);
    // 5xx와 429(rate limit)는 일시적 장애로 간주(그 외 4xx는 재시도 무의미)
    this.retryable = statusCode >= 500 || statusCode == 429;
    addDetail("statusCode", String.valueOf(statusCode));
  }

  public TmdbApiException(Throwable cause) {
    super(ErrorCode.EXTERNAL_API_ERROR, cause);
    this.retryable = true; // IO/타임아웃 등은 일시적 장애로 간주
  }

  public boolean isRetryable() {
    return retryable;
  }
}
