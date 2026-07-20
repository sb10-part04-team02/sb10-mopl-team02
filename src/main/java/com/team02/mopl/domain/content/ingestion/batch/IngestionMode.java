package com.team02.mopl.domain.content.ingestion.batch;

// 수집 실행 모드. Job 파라미터(JOB_PARAM_MODE)로 전달되어 각 스텝이 자기 모드일 때만 수집한다.
// - DAILY: 하루 1회. popular 영화/드라마 + SportsDB 시즌
// - HOURLY: 매시간. discover로 최신 -> 과거 백필 (영화/드라마)
public enum IngestionMode {
  DAILY,
  HOURLY
}
