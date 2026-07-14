package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.user.event.PasswordUpdatedEvent;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import com.team02.mopl.domain.user.event.UserLockUpdatedEvent;
import com.team02.mopl.global.outbox.redis.entity.RedisCommandOutbox;
import com.team02.mopl.global.outbox.redis.entity.enums.CommandType;
import com.team02.mopl.global.outbox.redis.entity.enums.OutboxTarget;
import com.team02.mopl.global.outbox.redis.service.RedisOutboxService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

  private final JwtRegistry jwtRegistry;
  private final NotificationService notificationService;
  private final RedisOutboxService outboxService;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRoleUpdated(RoleUpdatedEvent event) {
    try {
      // 권한변경시 refreshToken 전체삭제
      jwtRegistry.deleteAllRefreshToken(event.userId());
    } catch (DataAccessException e) {
      log.error(
          "[Redis] 유저 권한 변경 후 리프레시토큰 삭제 실패: userId={}, reason={}",
          event.userId(),
          e.getMessage(),
          e);
    }

    try {
      notificationService.createNotification(
          new NotificationCreateCommand(
              event.userId(),
              "권한 변경 알림",
              "회원님의 권한이 " + event.oldRole() + "에서 " + event.newRole() + "으로 변경되었습니다.",
              NotificationLevel.INFO,
              NotificationType.ROLE_UPDATED,
              null));
    } catch (RuntimeException e) {
      log.warn(
          "권한 변경 알림 생성 실패. userId={}, oldRole={}, newRole={}",
          event.userId(),
          event.oldRole(),
          event.newRole(),
          e);
    }
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000) // 1초 간격으로 최대 3번 재시도
      )
  public void onPasswordUpdated(PasswordUpdatedEvent event) {
    // 패스워드 변경시 refreshToken 전체삭제
    jwtRegistry.deleteAllRefreshToken(event.userId());
  }

  @Recover
  public void passwordUpdatedRecover(DataAccessException e, PasswordUpdatedEvent event) {
    UUID userId = event.userId();
    log.error("[Redis] 패스워드 변경 리프레시토큰 삭제 최종 실패: userId={}, reason={}", userId, e.getMessage(), e);

    // repository에 실패기록 직접 저장
    RedisCommandOutbox outbox =
        new RedisCommandOutbox(CommandType.DELETE_ALL_REFRESH_TOKEN, userId, OutboxTarget.USER);
    outboxService.saveOutbox(outbox);
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Retryable(
      retryFor = {DataAccessException.class},
      maxAttempts = 3,
      backoff = @Backoff(delay = 1000) // 1초 간격으로 최대 3번 재시도
      )
  public void onUserLockUpdatedEvent(UserLockUpdatedEvent event) {
    UUID userId = event.userId();

    if (event.locked()) {
      jwtRegistry.lockUser(userId);
    } else {
      jwtRegistry.unlockUser(userId);
    }
  }

  @Recover
  public void userLockUpdatedRecover(DataAccessException e, UserLockUpdatedEvent event) {
    String action = event.locked() ? "잠금(토큰삭제)" : "해제(키삭제)";
    log.error(
        "[Redis] 유저 계정 {} 최종 실패: userId={}, reason={}", action, event.userId(), e.getMessage(), e);
  }
}
