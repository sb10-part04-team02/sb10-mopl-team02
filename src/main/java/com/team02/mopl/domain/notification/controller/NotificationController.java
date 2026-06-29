package com.team02.mopl.domain.notification.controller;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

  private final NotificationService notificationService;

  // 인증된 사용자의 알림 목록을 최신순으로 조회한다.
  @Override
  @GetMapping
  public ResponseEntity<List<NotificationDto>> getNotifications(
      @AuthenticationPrincipal UUID receiverId) {
    return ResponseEntity.ok(notificationService.getNotifications(receiverId));
  }

  // 인증된 사용자의 알림을 읽음 처리한다.
  @Override
  @DeleteMapping("/{notificationId}")
  public ResponseEntity<Void> markAsRead(
      @AuthenticationPrincipal UUID receiverId, @PathVariable UUID notificationId) {
    notificationService.markAsRead(notificationId, receiverId);

    return ResponseEntity.noContent().build();
  }
}
