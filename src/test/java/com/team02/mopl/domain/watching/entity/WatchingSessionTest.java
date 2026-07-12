package com.team02.mopl.domain.watching.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.watching.exception.AlreadyExitedWatchingSessionException;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("WatchingSession 엔티티 단위 테스트")
class WatchingSessionTest {

  private WatchingSession activeSession() {
    return new WatchingSession(mock(Content.class), mock(User.class), Instant.now(), null);
  }

  @Test
  @DisplayName("exit()는 활성 세션의 종료 시각을 기록한다")
  void exit_setsExitedAt() {
    // given
    WatchingSession session = activeSession();

    // when
    assertThatCode(session::exit).doesNotThrowAnyException();

    // then
    assertThat(session.getExitedAt()).isNotNull();
  }

  @Test
  @DisplayName("이미 종료된 세션에 exit()를 호출하면 AlreadyExitedWatchingSessionException이 발생한다")
  void exit_alreadyExited_throws() {
    // given
    WatchingSession session = activeSession();
    session.exit();

    // when & then
    assertThatThrownBy(session::exit).isInstanceOf(AlreadyExitedWatchingSessionException.class);
  }
}
