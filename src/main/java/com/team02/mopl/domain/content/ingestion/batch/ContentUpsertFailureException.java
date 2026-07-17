package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.enums.ContentSource;

// 항목 단위 upsert 실패가 스텝 실패로 승격되는 상황을 나타내는 예외
// (1) 실패 건수가 skipLimit을 초과하거나 (2) 수집 대상 전부가 실패한 경우
public class ContentUpsertFailureException extends RuntimeException {

  private ContentUpsertFailureException(String message, Throwable cause) {
    super(message, cause);
  }

  public static ContentUpsertFailureException skipLimitExceeded(
      ContentSource source, int skipLimit, Throwable cause) {
    return new ContentUpsertFailureException(
        "수집 실패 건수가 skipLimit(" + skipLimit + ")을 초과했습니다. source=" + source, cause);
  }

  public static ContentUpsertFailureException allFailed(ContentSource source, int failed) {
    return new ContentUpsertFailureException(
        "수집 대상 전부가 실패했습니다. source=" + source + ", failed=" + failed, null);
  }
}
