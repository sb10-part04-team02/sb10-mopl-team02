package com.team02.mopl.domain.watching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.repository.ContentRepository;
import com.team02.mopl.domain.content.repository.TagRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.domain.watching.dto.WatchingSessionChange;
import com.team02.mopl.domain.watching.enums.ChangeType;
import com.team02.mopl.domain.watching.mapper.WatchingSessionMapper;
import com.team02.mopl.domain.watching.repository.WatchingSessionRepository;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 실제 스키마(부분 unique 인덱스 {@code uk_watching_sessions_content_user})를 로드한 상태에서 시청 세션 재참여 시나리오를 검증한다.
 *
 * <p>실제 {@link WatchingSessionService}를 실 DB에 태워, 한 유저가 같은 콘텐츠에 참여-이탈-재참여할 수 있어야 함을 단언한다. leave가
 * 소프트 삭제로 유니크 인덱스 자리를 비우지 않거나, join이 기존 세션을 재사용하지 않으면 이 테스트가 실패한다.
 */
class WatchingSessionServiceRejoinTest extends RepositoryTestSupport {

  @Autowired private WatchingSessionRepository watchingSessionRepository;
  @Autowired private ContentRepository contentRepository;
  @Autowired private TagRepository tagRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private EntityManager em;

  private WatchingSessionService watchingSessionService;

  private UUID contentId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    // 매핑 전용 컴포넌트(의존성 없음)라 직접 생성한다. @DataJpaTest는 서비스/컴포넌트 빈을 로드하지 않는다.
    watchingSessionService =
        new WatchingSessionService(
            watchingSessionRepository,
            contentRepository,
            tagRepository,
            userRepository,
            new WatchingSessionMapper());

    Content content = new Content(ContentType.MOVIE, "테스트 영화", "설명", "http://img");
    em.persist(content);
    User user = new User("시청자", "watcher@test.com", null, null, Role.USER, false);
    em.persist(user);
    em.flush();

    contentId = content.getId();
    userId = user.getId();
  }

  @Test
  @DisplayName("한 유저가 콘텐츠를 이탈한 뒤 다시 참여할 수 있고, 활성 세션은 1개로 유지된다")
  void join_afterLeave_canRejoin() {
    // given - 참여 후 이탈
    WatchingSessionChange joined = watchingSessionService.join(contentId, userId);
    watchingSessionService.leave(joined.watchingSession().id(), userId);

    // when & then - 재참여는 예외 없이 성공해야 하고, 활성 세션 수는 여전히 1이어야 한다
    assertThatCode(() -> watchingSessionService.join(contentId, userId)).doesNotThrowAnyException();
    assertThat(watchingSessionService.join(contentId, userId).type()).isEqualTo(ChangeType.JOIN);
    assertThat(watchingSessionRepository.countActiveByContentId(contentId)).isEqualTo(1L);
  }
}
