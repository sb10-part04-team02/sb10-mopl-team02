package com.team02.mopl.domain.subscription.service;

import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.subscription.entity.Subscription;
import com.team02.mopl.domain.subscription.event.SubscriptionCreatedEvent;
import com.team02.mopl.domain.subscription.exception.CannotSubscribeOwnPlaylistException;
import com.team02.mopl.domain.subscription.exception.SubscriptionAlreadyExistsException;
import com.team02.mopl.domain.subscription.exception.SubscriptionNotFoundException;
import com.team02.mopl.domain.subscription.repository.SubscriptionRepository;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.exception.UserNotFoundException;
import com.team02.mopl.domain.user.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
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
  private final ApplicationEventPublisher eventPublisher;

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

    Subscription subscription;
    try {
      subscription = subscriptionRepository.saveAndFlush(new Subscription(requesterId, playlist));
    } catch (DataIntegrityViolationException e) {
      throw new SubscriptionAlreadyExistsException(e);
    }

    playlistRepository.increaseSubscriberCount(playlistId);

    // 구독 트랜잭션 커밋 이후 알림을 생성해, 알림 실패가 구독 성공을 롤백하지 않도록 분리한다.
    eventPublisher.publishEvent(
        new SubscriptionCreatedEvent(
            subscription.getId(),
            subscriber.getId(),
            subscriber.getName(),
            playlist.getOwnerId(),
            playlist.getTitle()));

    log.info("플레이리스트 구독 성공: playlistId={}, requesterId={}", playlistId, requesterId);
  }

  @Transactional
  public void unsubscribe(UUID playlistId, UUID requesterId) {
    log.debug("플레이리스트 구독 취소 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    playlistRepository
        .findByIdAndDeletedAtIsNull(playlistId)
        .orElseThrow(PlaylistNotFoundException::new);

    int deleted = subscriptionRepository.softDeleteActive(requesterId, playlistId, Instant.now());
    if (deleted == 0) {
      throw new SubscriptionNotFoundException();
    }

    playlistRepository.decreaseSubscriberCount(playlistId);

    log.info("플레이리스트 구독 취소 성공: playlistId={}, requesterId={}", playlistId, requesterId);
  }

  private User getActiveUser(UUID userId) {
    return userRepository
        .findByIdAndDeletedAtIsNull(userId)
        .orElseThrow(UserNotFoundException::new);
  }
}
