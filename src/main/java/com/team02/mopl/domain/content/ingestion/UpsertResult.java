package com.team02.mopl.domain.content.ingestion;

/** 멱등 upsert 결과. 수집 실행 단위의 카운트 집계에 쓰인다. */
public enum UpsertResult {
  INSERTED,
  UPDATED,
  SKIPPED
}
