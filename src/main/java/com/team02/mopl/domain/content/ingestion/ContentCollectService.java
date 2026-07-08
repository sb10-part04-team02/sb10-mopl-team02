package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.enums.ContentSource;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// 등록된 모든 소스(TMDB, SportsDB ...)의 수집을 통합 관리하는 서비스
// fetch -> 건별 upsert 흐름을 소스 단위/항목 단위로 실패 격리하며 결과를 집계한다
// upsert가 건별 @Transactional이므로 이 서비스는 트랜잭션을 열지 않는다 (부분 실패 격리와 부합)
/*
TODO:
  contentIngestionJob
  ├─ tmdbStep:     ItemReader(페이지 순회) -> ItemProcessor(매퍼) -> ItemWriter(upsert)
  └─ sportsDbStep: ItemReader(리그 순회)   -> ItemProcessor(매퍼) -> ItemWriter(upsert)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentCollectService {

  private final List<ContentFetcher> contentFetchers; // 등록된 모든 소스 수집기
  private final ContentUpsertService contentUpsertService;

  // 전체 소스 수집. 소스 단위 실패는 error 로그 후 다음 소스를 계속 수집한다 (소스 간 격리)
  public List<CollectResult> collectAll() {
    List<CollectResult> results = new ArrayList<>();
    for (ContentFetcher fetcher : contentFetchers) {
      try {
        results.add(collect(fetcher));
      } catch (Exception e) {
        log.error("콘텐츠 수집 실패. source={}", fetcher.source(), e);
      }
    }
    return results;
  }

  // 소스 지정 수집 (주기 실행 스케줄러/수동 실행용). 미등록 소스면 IllegalArgumentException
  public CollectResult collect(ContentSource source) {
    ContentFetcher fetcher =
        contentFetchers.stream()
            .filter(f -> f.source() == source)
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("등록되지 않은 콘텐츠 소스입니다: " + source));
    return collect(fetcher);
  }

  // fetch 후 건별 upsert. 항목 단위 실패는 warn + failed 카운트로 격리한다
  private CollectResult collect(ContentFetcher fetcher) {
    List<ExternalContentData> contents = fetcher.fetch(); // 페이지/리그 단위 실패는 fetcher가 이미 격리
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
    CollectResult result =
        new CollectResult(fetcher.source(), contents.size(), inserted, updated, skipped, failed);
    log.info(
        "콘텐츠 수집 완료. source={}, fetched={}, inserted={}, updated={}, skipped={}, failed={}",
        result.source(),
        result.fetched(),
        result.inserted(),
        result.updated(),
        result.skipped(),
        result.failed());
    return result;
  }
}
