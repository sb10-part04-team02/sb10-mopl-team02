package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import com.team02.mopl.domain.user.event.UserLockUpdatedEvent;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventListener {

  private final JwtRegistry jwtRegistry;

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
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserLockUpdatedEvent(UserLockUpdatedEvent event) {
    UUID userId = event.userId();

    try {
      if (event.locked()) {
        jwtRegistry.lockUser(userId);
      } else {
        jwtRegistry.unlockUser(userId);
      }
    } catch (DataAccessException e) {
      String action = event.locked() ? "잠금(토큰삭제)" : "해제(키삭제)";
      log.error("[Redis] 유저 계정 {} 실패: userId={}, reason={}", action, userId, e.getMessage(), e);
    }
  }
}
