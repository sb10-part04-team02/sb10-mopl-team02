package com.team02.mopl.domain.playlist.service;

import com.team02.mopl.domain.follow.entity.Follow;
import com.team02.mopl.domain.follow.repository.FollowRepository;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.playlist.event.PlaylistCreatedEvent;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaylistCreatedEventListener {

  private final FollowRepository followRepository;
  private final NotificationService notificationService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onPlaylistCreated(PlaylistCreatedEvent event) {
    List<Follow> followers = followRepository.findByFollowee_IdAndDeletedAtIsNull(event.ownerId());

    for (Follow follow : followers) {
      try {
        notificationService.createNotification(
            new NotificationCreateCommand(
                follow.getFollower().getId(),
                event.ownerName() + "님이 플레이리스트를 만들었어요.",
                "[" + event.playlistTitle() + "] " + event.playlistDescription(),
                NotificationLevel.INFO,
                NotificationType.FOLLOWING_USER_ACTIVITY));
      } catch (RuntimeException e) {
        log.warn(
            "플레이리스트 생성 알림 생성 실패. ownerId={}, followerId={}",
            event.ownerId(),
            follow.getFollower().getId(),
            e);
      }
    }
  }
}
