package com.team02.mopl.domain.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.watching.repository.WatcherCountProjection;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("WatcherCountService 단위 테스트")
class WatcherCountServiceTest {

  @Mock WatchingSessionRepository watchingSessionRepository;

  @InjectMocks WatcherCountService watcherCountService;

  private WatcherCountProjection projection(UUID contentId, long count) {
    WatcherCountProjection p = mock(WatcherCountProjection.class);
    given(p.getContentId()).willReturn(contentId);
    given(p.getCount()).willReturn(count);
    return p;
  }

  @Nested
  @DisplayName("count - 단건 활성 시청자 수 집계")
  class Count {

    @Test
    @DisplayName("repository의 countActiveByContentId 결과를 그대로 위임 반환한다")
    void delegatesToRepository() {
      // given
      UUID contentId = UUID.randomUUID();
      given(watchingSessionRepository.countActiveByContentId(contentId)).willReturn(7L);

      // when
      long result = watcherCountService.count(contentId);

      // then
      assertThat(result).isEqualTo(7L);
      then(watchingSessionRepository).should().countActiveByContentId(contentId);
    }
  }

  @Nested
  @DisplayName("countByContentIds - 다건 활성 시청자 수 일괄 집계")
  class CountByContentIds {

    @Test
    @DisplayName("contentIds가 null이면 빈 Map을 반환하고 repository를 호출하지 않는다")
    void returnsEmpty_whenNull() {
      // when
      Map<UUID, Long> result = watcherCountService.countByContentIds(null);

      // then
      assertThat(result).isEmpty();
      then(watchingSessionRepository).should(never()).countActiveByContentIds(anyList());
    }

    @Test
    @DisplayName("contentIds가 비어 있으면 빈 Map을 반환하고 repository를 호출하지 않는다")
    void returnsEmpty_whenEmpty() {
      // when
      Map<UUID, Long> result = watcherCountService.countByContentIds(List.of());

      // then
      assertThat(result).isEmpty();
      then(watchingSessionRepository).should(never()).countActiveByContentIds(anyList());
    }

    @Test
    @DisplayName("Projection 목록을 contentId->count Map으로 변환한다(결과에 없는 콘텐츠는 Map에도 없음)")
    void mapsProjectionsToMap() {
      // given
      UUID a = UUID.randomUUID();
      UUID b = UUID.randomUUID();
      UUID c = UUID.randomUUID(); // 시청자 0이라 projection 결과에 미포함
      WatcherCountProjection pa = projection(a, 3L);
      WatcherCountProjection pb = projection(b, 1L);
      given(watchingSessionRepository.countActiveByContentIds(any())).willReturn(List.of(pa, pb));

      // when
      Map<UUID, Long> result = watcherCountService.countByContentIds(List.of(a, b, c));

      // then
      assertThat(result).containsOnly(Map.entry(a, 3L), Map.entry(b, 1L));
      assertThat(result).doesNotContainKey(c);
    }
  }
}
