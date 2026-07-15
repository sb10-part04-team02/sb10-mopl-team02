package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.SkipListener;
import org.springframework.stereotype.Component;

// 항목 단위 upsert 실패(skip)를 기록하는 리스너 (기존 건별 실패 warn 로그와 동등)
@Slf4j
@Component
public class IngestionSkipListener
    implements SkipListener<ExternalContentData, ExternalContentData> {

  @Override
  public void onSkipInWrite(ExternalContentData item, Throwable t) {
    log.warn("콘텐츠 upsert 실패로 건너뜁니다. source={}, externalId={}", item.source(), item.externalId(), t);
  }
}
