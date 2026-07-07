package com.team02.mopl.domain.user.service;

import com.team02.mopl.domain.auth.jwt.JwtRegistry;
import com.team02.mopl.domain.user.event.RoleUpdatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class UserEventListener {

  private final JwtRegistry jwtRegistry;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onUserRoleUpdated(RoleUpdatedEvent event) {
    // 권한변경
    jwtRegistry.deleteAllRefreshToken(event.userId());
  }
}
