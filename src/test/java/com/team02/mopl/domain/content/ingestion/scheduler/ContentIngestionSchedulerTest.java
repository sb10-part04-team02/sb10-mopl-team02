package com.team02.mopl.domain.content.ingestion.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.CollectResult;
import com.team02.mopl.domain.content.ingestion.ContentCollectService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentIngestionSchedulerTest {

  private static final String TOKEN = "lock-token";

  @Mock private ContentCollectService contentCollectService;
  @Mock private IngestionRunLock runLock;

  private ContentIngestionScheduler scheduler;

  @BeforeEach
  void setUp() {
    IngestionSchedulerProperties properties =
        new IngestionSchedulerProperties(true, "0 0 4 * * *", Duration.ofMinutes(30));
    scheduler = new ContentIngestionScheduler(contentCollectService, runLock, properties);
  }

  @Test
  @DisplayName("락 획득에 성공하면 전체 소스 수집을 실행하고 락을 해제한다")
  void collectAll_whenLockAcquired_collectsAndReleases() {
    // given - 락 획득이 성공해서 TOKEN을 돌려주는 상황
    given(runLock.tryAcquire(any())).willReturn(TOKEN);
    given(contentCollectService.collectAll())
        .willReturn(List.of(new CollectResult(ContentSource.TMDB, 10, 5, 3, 2, 0)));

    // when
    scheduler.collectAll();

    // then
    then(contentCollectService).should().collectAll();
    then(runLock).should().release(TOKEN); // 락 해제 호출 확인
  }

  @Test
  @DisplayName("락 획득에 실패하면(이미 실행 중) 수집을 건너뛴다")
  void collectAll_whenLockNotAcquired_skips() {
    // given
    given(runLock.tryAcquire(any())).willReturn(null);

    // when
    scheduler.collectAll();

    // then
    then(contentCollectService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("수집이 실패해도 예외를 전파하지 않고 락은 해제한다 (스케줄 지속 보장)")
  void collectAll_whenCollectFails_doesNotPropagateAndReleases() {
    // given
    given(runLock.tryAcquire(any())).willReturn(TOKEN);
    given(contentCollectService.collectAll()).willThrow(new RuntimeException("수집 실패"));

    // when & then
    assertThatCode(() -> scheduler.collectAll()).doesNotThrowAnyException();
    then(runLock).should().release(TOKEN);
  }

  @Test
  @DisplayName("락 해제 중 Redis 오류가 나도 예외를 전파하지 않는다 (정상 완료 보장, TTL이 안전망)")
  void collectAll_whenReleaseFails_doesNotPropagate() {
    // given - 수집은 성공했지만 락 해제에서 Redis 오류가 발생하는 상황
    given(runLock.tryAcquire(any())).willReturn(TOKEN);
    given(contentCollectService.collectAll())
        .willReturn(List.of(new CollectResult(ContentSource.TMDB, 10, 5, 3, 2, 0)));
    willThrow(new RuntimeException("Redis 오류")).given(runLock).release(TOKEN);

    // when & then - 해제 실패가 성공한 수집을 예외로 뒤바꾸지 않아야 한다
    assertThatCode(() -> scheduler.collectAll()).doesNotThrowAnyException();
    then(contentCollectService).should().collectAll();
  }

  @Test
  @DisplayName("락 획득 중 Redis 오류가 나면 예외를 전파하지 않고 수집을 건너뛴다")
  void collectAll_whenLockAcquireFails_doesNotPropagateAndSkips() {
    // given
    given(runLock.tryAcquire(any())).willThrow(new RuntimeException("Redis 오류"));

    // when & then
    assertThatCode(() -> scheduler.collectAll()).doesNotThrowAnyException();
    then(contentCollectService).shouldHaveNoInteractions();
  }
}
