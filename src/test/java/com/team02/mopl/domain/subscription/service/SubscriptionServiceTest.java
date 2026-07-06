package com.team02.mopl.domain.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.subscription.entity.Subscription;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceTest {

  @Mock private PlaylistRepository playlistRepository;

  @Mock private SubscriptionRepository subscriptionRepository;

  @Mock private UserRepository userRepository;

  @Mock private NotificationService notificationService;

  @InjectMocks private SubscriptionService subscriptionService;

  @Test
  @DisplayName("구독에 성공하면 구독자 수가 증가하고 소유자에게 알림을 전송한다")
  void subscribe_success() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");
    User subscriber = mock(User.class);
    given(subscriber.getName()).willReturn("구독자");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));
    given(
            subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
                requesterId, playlistId))
        .willReturn(false);
    given(userRepository.findByIdAndDeletedAtIsNull(requesterId))
        .willReturn(Optional.of(subscriber));

    subscriptionService.subscribe(playlistId, requesterId);

    verify(playlistRepository).increaseSubscriberCount(playlistId);
    verify(subscriptionRepository).saveAndFlush(any(Subscription.class));

    ArgumentCaptor<NotificationCreateCommand> commandCaptor =
        ArgumentCaptor.forClass(NotificationCreateCommand.class);
    verify(notificationService).createNotification(commandCaptor.capture());

    NotificationCreateCommand command = commandCaptor.getValue();
    assertThat(command.receiverId()).isEqualTo(ownerId);
    assertThat(command.level()).isEqualTo(NotificationLevel.INFO);
    assertThat(command.notificationType()).isEqualTo(NotificationType.PLAYLIST_SUBSCRIBED);
    assertThat(command.content()).contains("구독자");
  }

  @Test
  @DisplayName("본인 소유 플레이리스트를 구독하면 CANNOT_SUBSCRIBE_OWN_PLAYLIST 예외가 발생한다")
  void subscribe_ownPlaylist_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));

    assertThatThrownBy(() -> subscriptionService.subscribe(playlistId, ownerId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.CANNOT_SUBSCRIBE_OWN_PLAYLIST));

    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("이미 구독 중이면 SUBSCRIPTION_ALREADY_EXISTS 예외가 발생한다")
  void subscribe_alreadyExists_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));
    given(userRepository.findByIdAndDeletedAtIsNull(requesterId))
        .willReturn(Optional.of(mock(User.class)));
    given(
            subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
                requesterId, playlistId))
        .willReturn(true);

    assertThatThrownBy(() -> subscriptionService.subscribe(playlistId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_EXISTS));

    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("구독 대상 플레이리스트가 없으면 PLAYLIST_NOT_FOUND 예외가 발생한다")
  void subscribe_playlistNotFound_throwsException() {
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> subscriptionService.subscribe(playlistId, requesterId))
        .isInstanceOf(PlaylistNotFoundException.class);
  }

  @Test
  @DisplayName("구독 요청자가 존재하지 않으면 USER_NOT_FOUND 예외가 발생한다")
  void subscribe_requesterNotFound_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));
    given(userRepository.findByIdAndDeletedAtIsNull(requesterId)).willReturn(Optional.empty());

    assertThatThrownBy(() -> subscriptionService.subscribe(playlistId, requesterId))
        .isInstanceOf(UserNotFoundException.class);

    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("동시 구독 요청으로 유니크 제약이 발생하면 SUBSCRIPTION_ALREADY_EXISTS 예외가 발생한다")
  void subscribe_duplicateByUniqueConstraint_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));
    given(userRepository.findByIdAndDeletedAtIsNull(requesterId))
        .willReturn(Optional.of(mock(User.class)));
    given(
            subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
                requesterId, playlistId))
        .willReturn(false);
    given(subscriptionRepository.saveAndFlush(any(Subscription.class)))
        .willThrow(new DataIntegrityViolationException("중복 구독"));

    assertThatThrownBy(() -> subscriptionService.subscribe(playlistId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_ALREADY_EXISTS));

    verifyNoInteractions(notificationService);
  }

  @Test
  @DisplayName("구독을 취소하면 활성 구독이 논리 삭제되고 구독자 수가 감소한다")
  void unsubscribe_success() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));
    given(subscriptionRepository.softDeleteActive(eq(requesterId), eq(playlistId), any()))
        .willReturn(1);

    subscriptionService.unsubscribe(playlistId, requesterId);

    verify(playlistRepository).decreaseSubscriberCount(playlistId);
  }

  @Test
  @DisplayName("활성 구독이 없으면 SUBSCRIPTION_NOT_FOUND 예외가 발생하고 구독자 수는 감소하지 않는다")
  void unsubscribe_notFound_throwsException() {
    UUID ownerId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();
    UUID playlistId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "제목", "설명");

    given(playlistRepository.findByIdAndDeletedAtIsNull(playlistId))
        .willReturn(Optional.of(playlist));
    given(subscriptionRepository.softDeleteActive(eq(requesterId), eq(playlistId), any()))
        .willReturn(0);

    assertThatThrownBy(() -> subscriptionService.unsubscribe(playlistId, requesterId))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SUBSCRIPTION_NOT_FOUND));

    verify(playlistRepository, never()).decreaseSubscriberCount(playlistId);
  }
}
