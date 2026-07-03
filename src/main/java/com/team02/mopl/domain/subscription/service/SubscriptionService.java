package com.team02.mopl.domain.subscription.service;

import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.subscription.entity.Subscription;
import com.team02.mopl.domain.subscription.exception.CannotSubscribeOwnPlaylistException;
import com.team02.mopl.domain.subscription.exception.SubscriptionAlreadyExistsException;
import com.team02.mopl.domain.subscription.exception.SubscriptionNotFoundException;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SubscriptionService {

  private final PlaylistRepository playlistRepository;
  private final SubscriptionRepository subscriptionRepository;
  private final UserRepository userRepository;
  private final NotificationService notificationService;

  // 플레이리스트를 구독하고, 소유자에게 알림을 생성한다.
  @Transactional
  public void subscribe(UUID playlistId, UUID requesterId) {
    log.debug("플레이리스트 구독 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    Playlist playlist =
        playlistRepository
            .findByIdAndDeletedAtIsNull(playlistId)
            .orElseThrow(PlaylistNotFoundException::new);

    if (playlist.getOwnerId().equals(requesterId)) {
      throw new CannotSubscribeOwnPlaylistException();
    }

    User subscriber = getActiveUser(requesterId);

    if (subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
        requesterId, playlistId)) {
      throw new SubscriptionAlreadyExistsException();
    }

    try {
      subscriptionRepository.saveAndFlush(new Subscription(requesterId, playlist));
    } catch (DataIntegrityViolationException e) {
      throw new SubscriptionAlreadyExistsException();
    }

    playlistRepository.increaseSubscriberCount(playlistId);

    // TODO: notifyOwner가 구독과 같은 트랜잭션에서 동기로 실행되어, 소유자 계정이 비활성이면
    //  createNotification 내부의 getActiveUser(ownerId)가 USER_NOT_FOUND를 던지면서 구독 자체가
    //  롤백된다. 알림 발송을 AFTER_COMMIT 이벤트 리스너로 분리해 알림 실패가 구독을 롤백시키지 않도록
    //  개선 필요. (참고: DmSentEvent + DmEventListener 패턴, PR #261의 PlaylistService.create 동일 이슈)
    notifyOwner(playlist, subscriber);

    log.info("플레이리스트 구독 성공: playlistId={}, requesterId={}", playlistId, requesterId);
  }

  // 플레이리스트 구독을 취소한다.
  @Transactional
  public void unsubscribe(UUID playlistId, UUID requesterId) {
    log.debug("플레이리스트 구독 취소 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    playlistRepository
        .findByIdAndDeletedAtIsNull(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    Subscription subscription =
        subscriptionRepository
            .findByUserIdAndPlaylist_IdAndDeletedAtIsNull(requesterId, playlistId)
            .orElseThrow(SubscriptionNotFoundException::new);

    subscription.delete();
    playlistRepository.decreaseSubscriberCount(playlistId);

    log.info("플레이리스트 구독 취소 성공: playlistId={}, requesterId={}", playlistId, requesterId);
  }

  private User getActiveUser(UUID userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(UserNotFoundException::new);
  }

  // 구독자 이름으로 소유자에게 구독 알림을 전송한다.
  private void notifyOwner(Playlist playlist, User subscriber) {
    notificationService.createNotification(
        new NotificationCreateCommand(
            playlist.getOwnerId(),
            "플레이리스트 구독 알림",
            subscriber.getName() + "님이 회원님의 플레이리스트를 구독했습니다.",
            NotificationLevel.INFO,
            NotificationType.PLAYLIST_SUBSCRIBED));
  }
}
