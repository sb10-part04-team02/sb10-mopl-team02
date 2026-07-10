package com.team02.mopl.domain.content.ingestion.scheduler;

import com.team02.mopl.domain.content.ingestion.CollectResult;
import com.team02.mopl.domain.content.ingestion.ContentCollectService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// 콘텐츠 수집을 주기 실행하는 스케줄러 (app.ingestion.scheduler.enabled=true일 때만 등록)
// - IngestionRunLock으로 인스턴스 간 중복 실행을 방지한다 (획득 실패 시 이번 주기 skip)
// - 수집 실패가 다음 주기 실행을 막지 않도록 예외는 로그만 남김 (러너와 동일 정책)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ingestion.scheduler.enabled", havingValue = "true")
public class ContentIngestionScheduler {

  private final ContentCollectService contentCollectService;
  private final IngestionRunLock runLock;
  private final IngestionSchedulerProperties properties;

  @Scheduled(cron = "${app.ingestion.scheduler.cron}", zone = "Asia/Seoul")
  public void collectAll() {
    String token; // 락 해제 시 소유권 증명에 사용할 토큰
    try {
      token = runLock.tryAcquire(properties.lockTtl()); // 락 획득 시도
    } catch (Exception e) {
      // Redis 장애 시 fail-closed: 이번 주기는 건너뛰고 다음 cron에 재시도 (수집은 지연 허용 배치)
      log.error("수집 락 획득 실패(Redis 오류). 이번 주기 수집을 건너뜁니다.", e);
      return;
    }
    if (token == null) {
      // 다른 인스턴스가 락을 이미 가지고 있는 경우
      log.info("콘텐츠 수집이 이미 실행 중입니다. 이번 주기를 건너뜁니다.");
      return;
    }
    long startedAt = System.currentTimeMillis();
    log.info("스케줄 콘텐츠 수집 시작");
    try {
      List<CollectResult> results = contentCollectService.collectAll();
      long elapsedMs = System.currentTimeMillis() - startedAt;
      int fetched = results.stream().mapToInt(CollectResult::fetched).sum();
      int inserted = results.stream().mapToInt(CollectResult::inserted).sum();
      int updated = results.stream().mapToInt(CollectResult::updated).sum();
      int skipped = results.stream().mapToInt(CollectResult::skipped).sum();
      int failed = results.stream().mapToInt(CollectResult::failed).sum();
      // 소스 단위 실패는 서비스가 이미 error 로그를 남기고 results에서 빠지므로 sources 수로 드러난다
      if (failed > 0) {
        log.warn(
            "스케줄 콘텐츠 수집 완료(일부 항목 실패). sources={}, fetched={}, inserted={}, updated={}, skipped={}, failed={}, elapsedMs={}",
            results.size(),
            fetched,
            inserted,
            updated,
            skipped,
            failed,
            elapsedMs);
      } else {
        log.info(
            "스케줄 콘텐츠 수집 완료. sources={}, fetched={}, inserted={}, updated={}, skipped={}, failed={}, elapsedMs={}",
            results.size(),
            fetched,
            inserted,
            updated,
            skipped,
            failed,
            elapsedMs);
      }
    } catch (Exception e) {
      log.error("스케줄 콘텐츠 수집 실패. elapsedMs={}", System.currentTimeMillis() - startedAt, e);
    } finally {
      try {
        runLock.release(token); // 락 해제가 실패해도 TTL이 최종 안전망
      } catch (Exception e) {
        log.warn("수집 락 해제 실패(Redis 오류). TTL 만료로 자동 해제됩니다.", e);
      }
    }
  }
}
