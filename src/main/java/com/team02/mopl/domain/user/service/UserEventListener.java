package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.notification.dto.NotificationCreateCommand;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.entity.enums.NotificationType;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
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

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRoleUpdated(RoleUpdatedEvent event) {
    try {
      // 권한변경
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
              NotificationType.ROLE_UPDATED));
    } catch (RuntimeException e) {
      log.warn(
          "권한 변경 알림 생성 실패. userId={}, oldRole={}, newRole={}",
          event.userId(),
          event.oldRole(),
          event.newRole(),
          e);
    }
  }
}
