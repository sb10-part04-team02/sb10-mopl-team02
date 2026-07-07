package com.team02.mopl.domain.content.ingestion.tmdb;

import com.team02.mopl.domain.content.ingestion.ContentUpsertService;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기동 시 1회 TMDB 수집을 실행하는 검증/수동 실행용 진입점(app.tmdb.run-on-startup=true일 때만 등록).
 *
 * <p>주기 실행 스케줄러(#289)와 소스 통합 오케스트레이션(#288)이 이 자리를 대체할 예정이다. 수집 실패가 애플리케이션 기동을 막지 않도록 예외는 모두 잡아 로그만
 * 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.tmdb.run-on-startup", havingValue = "true")
public class TmdbIngestionRunner implements ApplicationRunner {

  private final TmdbContentFetcher tmdbContentFetcher;
  private final ContentUpsertService contentUpsertService;

  @Override
  public void run(ApplicationArguments args) {
    try {
      ingest();
    } catch (Exception e) {
      log.error("TMDB 수집 실행에 실패했습니다.", e);
    }
  }

  private void ingest() {
    List<ExternalContentData> contents = tmdbContentFetcher.fetch();
    int inserted = 0;
    int updated = 0;
    int skipped = 0;
    int failed = 0;
    for (ExternalContentData data : contents) {
      try {
        switch (contentUpsertService.upsert(data)) {
          case INSERTED -> inserted++;
          case UPDATED -> updated++;
          case SKIPPED -> skipped++;
        }
      } catch (Exception e) {
        failed++;
        log.warn("콘텐츠 upsert 실패. source={}, externalId={}", data.source(), data.externalId(), e);
      }
    }
    log.info(
        "TMDB 수집 완료. fetched={}, inserted={}, updated={}, skipped={}, failed={}",
        contents.size(),
        inserted,
        updated,
        skipped,
        failed);
  }
}
