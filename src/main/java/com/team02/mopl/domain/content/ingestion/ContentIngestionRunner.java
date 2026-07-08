package com.team02.mopl.domain.content.ingestion;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

// 기동 시 1회 전체 소스 수집을 실행하는 검증/수동 실행용 진입점(app.ingestion.run-on-startup=true일 때만 등록)
// 수집 실패가 애플리케이션 기동을 막지 않도록 예외는 로그만 남김
// TODO: 주기 실행 스케줄러가 대체 예정 (스케줄러는 collectAll()/collect(source)를 그대로 호출)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ingestion.run-on-startup", havingValue = "true")
public class ContentIngestionRunner implements ApplicationRunner {

  private final ContentCollectService contentCollectService;

  @Override
  public void run(ApplicationArguments args) {
    try {
      contentCollectService.collectAll();
    } catch (Exception e) {
      log.error("콘텐츠 수집 실행에 실패했습니다.", e);
    }
  }
}
