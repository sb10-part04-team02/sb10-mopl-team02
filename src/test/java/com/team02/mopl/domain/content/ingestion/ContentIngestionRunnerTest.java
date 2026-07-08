package com.team02.mopl.domain.content.ingestion;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentIngestionRunnerTest {

  @Mock private ContentCollectService contentCollectService;

  @InjectMocks private ContentIngestionRunner runner;

  @Test
  @DisplayName("기동 시 전체 소스 수집을 1회 실행한다")
  void run_triggersCollectAll() {
    // given - 수집 서비스 정상 동작
    // when
    runner.run(null);

    // then
    then(contentCollectService).should().collectAll();
  }

  @Test
  @DisplayName("수집이 실패해도 예외를 전파하지 않는다 (애플리케이션 기동 보호)")
  void run_whenCollectFails_doesNotPropagate() {
    // given
    given(contentCollectService.collectAll()).willThrow(new RuntimeException("수집 실패"));

    // when & then
    assertThatCode(() -> runner.run(null)).doesNotThrowAnyException();
  }
}
