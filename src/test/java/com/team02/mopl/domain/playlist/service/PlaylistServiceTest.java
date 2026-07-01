package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.mapper.PlaylistMapper;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// TODO: JWT 인증 연결 후 PlaylistController @WebMvcTest 추가
//       (201 응답 / @Valid 검증 실패 400 / 인증된 요청자 UUID 바인딩 검증).
@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock PlaylistRepository playlistRepository;

  @Mock PlaylistMapper playlistMapper;

  @Mock FollowRepository followRepository;

  @Mock NotificationService notificationService;

  @Mock UserRepository userRepository;

  @InjectMocks PlaylistService playlistService;

  @Captor ArgumentCaptor<Playlist> playlistCaptor;

  @Nested
  class Create {
    private final UUID ownerId = UUID.randomUUID();
    private final String ownerName = "우디";
    private final String title = "내 플리";
    private final String description = "설명";

    @Test
    @DisplayName("정상 요청이면 소유자를 요청자로 지정해 저장하고 PlaylistDto를 반환한다")
    void success_whenRequestIsValid() {
      User owner = mockUserWithId(ownerId);
      PlaylistCreateRequest request = new PlaylistCreateRequest(title, description);
      Playlist saved = new Playlist(ownerId, title, description);
      PlaylistDto expect =
          new PlaylistDto(
              UUID.randomUUID(),
              new UserSummary(ownerId, null, null),
              title,
              description,
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());

      // 플레이리스트 생성 전 owner가 존재하는지 확인
      given(userRepository.findByIdAndDeletedAtIsNull(ownerId)).willReturn(Optional.of(owner));
      given(playlistRepository.save(any(Playlist.class))).willReturn(saved);
      // 팔로워가 없으면 주요 활동 알림을 생성 X
      given(followRepository.findByFollowee_IdAndDeletedAtIsNull(ownerId)).willReturn(List.of());
      given(playlistMapper.toDto(any(Playlist.class), eq(false))).willReturn(expect);

      PlaylistDto actual = playlistService.create(ownerId, request);

      assertThat(actual).isEqualTo(expect);
      then(playlistRepository).should().save(playlistCaptor.capture());
      then(playlistMapper).should().toDto(any(Playlist.class), eq(false));
      then(notificationService).shouldHaveNoInteractions();

      Playlist captured = playlistCaptor.getValue();
      assertThat(captured.getOwnerId()).isEqualTo(ownerId);
      assertThat(captured.getTitle()).isEqualTo(title);
      assertThat(captured.getDescription()).isEqualTo(description);
    }

    @Test
    @DisplayName("플레이리스트를 생성하면 생성자를 팔로우 중인 사용자들에게 주요 활동 알림을 생성한다")
    void success_sendsFollowingUserActivityNotifications() {
      UUID followerId = UUID.randomUUID();
      User owner = mockUserWithIdAndName(ownerId, ownerName);
      User follower = mockUserWithId(followerId);
      Follow follow = new Follow(follower, owner);

      PlaylistCreateRequest request = new PlaylistCreateRequest(title, description);
      Playlist saved = new Playlist(ownerId, title, description);
      PlaylistDto expect =
          new PlaylistDto(
              UUID.randomUUID(),
              new UserSummary(ownerId, null, null),
              title,
              description,
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());

      given(userRepository.findByIdAndDeletedAtIsNull(ownerId)).willReturn(Optional.of(owner));
      given(playlistRepository.save(any(Playlist.class))).willReturn(saved);
      // 플레이리스트 생성자를 팔로우 중인 사용자 목록을 조회
      given(followRepository.findByFollowee_IdAndDeletedAtIsNull(ownerId))
          .willReturn(List.of(follow));
      given(playlistMapper.toDto(any(Playlist.class), eq(false))).willReturn(expect);

      playlistService.create(ownerId, request);

      ArgumentCaptor<NotificationCreateCommand> commandCaptor =
          ArgumentCaptor.forClass(NotificationCreateCommand.class);

      then(notificationService).should().createNotification(commandCaptor.capture());

      NotificationCreateCommand command = commandCaptor.getValue();

      assertThat(command.receiverId()).isEqualTo(followerId);
      assertThat(command.title()).isEqualTo(ownerName + "님이 플레이리스트를 만들었어요.");
      assertThat(command.content()).isEqualTo("[" + title + "] " + description);
      assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
      assertThat(command.notificationType()).isEqualTo(NotificationType.FOLLOWING_USER_ACTIVITY);
    }

    private User mockUserWithId(UUID id) {
      User user = mock(User.class);
      given(user.getId()).willReturn(id);
      return user;
    }

    private User mockUserWithIdAndName(UUID id, String name) {
      User user = mockUserWithId(id);
      given(user.getName()).willReturn(name);
      return user;
    }
  }
}
