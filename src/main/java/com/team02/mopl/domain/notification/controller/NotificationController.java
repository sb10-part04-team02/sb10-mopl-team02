package com.team02.mopl.domain.notification.controller;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.dto.NotificationSearchRequest;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.global.dto.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

  private final NotificationService notificationService;

  // 인증된 사용자의 알림 목록을 커서 기반 페이지네이션으로 조회한다.
  @Override
  @GetMapping
  public ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
      @AuthenticationPrincipal UUID receiverId,
      @ParameterObject @ModelAttribute @Valid NotificationSearchRequest request) {
    return ResponseEntity.ok(notificationService.getNotifications(receiverId, request));
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
