package com.team02.mopl.domain.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRepositoryTest {

  private final SseEmitterRepository sseEmitterRepository = new SseEmitterRepository();

  @Test
  @DisplayName("사용자 ID 기준으로 emitter를 저장하고 조회한다")
  void saveAndFindByUserId_success() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter();

    Optional<SseEmitter> previous = sseEmitterRepository.save(userId, emitter);
    Optional<SseEmitter> result = sseEmitterRepository.findByUserId(userId);

    assertThat(previous).isEmpty();
    assertThat(result).containsSame(emitter);
  }

  @Test
  @DisplayName("같은 사용자 ID로 emitter를 다시 저장하면 기존 emitter를 반환하고 교체한다")
  void save_sameUserId_returnsPreviousEmitterAndReplaces() {
    UUID userId = UUID.randomUUID();
    SseEmitter oldEmitter = new SseEmitter();
    SseEmitter newEmitter = new SseEmitter();

    sseEmitterRepository.save(userId, oldEmitter);

    Optional<SseEmitter> previous = sseEmitterRepository.save(userId, newEmitter);
    Optional<SseEmitter> result = sseEmitterRepository.findByUserId(userId);

    assertThat(previous).containsSame(oldEmitter);
    assertThat(result).containsSame(newEmitter);
  }

  @Test
  @DisplayName("저장되지 않은 사용자 ID로 조회하면 빈 Optional을 반환한다")
  void findByUserId_notFound_returnsEmpty() {
    UUID userId = UUID.randomUUID();

    Optional<SseEmitter> result = sseEmitterRepository.findByUserId(userId);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("사용자 ID와 emitter가 모두 일치하면 삭제한다")
  void delete_sameEmitter_removesEmitter() {
    UUID userId = UUID.randomUUID();
    SseEmitter emitter = new SseEmitter();

    sseEmitterRepository.save(userId, emitter);

    sseEmitterRepository.delete(userId, emitter);

    assertThat(sseEmitterRepository.findByUserId(userId)).isEmpty();
  }

  @Test
  @DisplayName("사용자 ID가 같아도 emitter가 다르면 삭제하지 않는다")
  void delete_differentEmitter_doesNotRemoveEmitter() {
    UUID userId = UUID.randomUUID();
    SseEmitter savedEmitter = new SseEmitter();
    SseEmitter otherEmitter = new SseEmitter();

    sseEmitterRepository.save(userId, savedEmitter);

    sseEmitterRepository.delete(userId, otherEmitter);

    assertThat(sseEmitterRepository.findByUserId(userId)).containsSame(savedEmitter);
  }
}
