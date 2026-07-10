package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.enums.ContentSource;

// 소스 1개 수집 실행의 결과 집계
public record CollectResult(
    ContentSource source, int fetched, int inserted, int updated, int skipped, int failed) {}
