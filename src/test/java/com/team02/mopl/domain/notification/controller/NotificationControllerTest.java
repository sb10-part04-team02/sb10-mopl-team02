package com.team02.mopl.domain.notification.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.exception.NotificationForbiddenException;
import com.team02.mopl.domain.notification.exception.NotificationNotFoundException;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificationController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class NotificationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private NotificationService notificationService;

  @Test
  @DisplayName("알림 목록 조회가 성공하면 200과 알림 목록을 반환한다")
  void getNotifications_success() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    NotificationDto notification =
        new NotificationDto(
            notificationId,
            Instant.parse("2026-06-29T00:00:00Z"),
            receiverId,
            "알림 제목",
            "알림 내용",
            NotificationLevel.INFO);

    given(notificationService.getNotifications(receiverId)).willReturn(List.of(notification));

    mockMvc
        .perform(
            get("/api/notifications").with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(notificationId.toString()))
        .andExpect(jsonPath("$[0].receiverId").value(receiverId.toString()))
        .andExpect(jsonPath("$[0].title").value("알림 제목"))
        .andExpect(jsonPath("$[0].content").value("알림 내용"))
        .andExpect(jsonPath("$[0].level").value("INFO"));

    verify(notificationService).getNotifications(receiverId);
  }

  @Test
  @DisplayName("알림 목록이 비어 있으면 200과 빈 배열을 반환한다")
  void getNotifications_empty_success() throws Exception {
    UUID receiverId = UUID.randomUUID();

    given(notificationService.getNotifications(receiverId)).willReturn(List.of());

    mockMvc
        .perform(
            get("/api/notifications").with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());

    verify(notificationService).getNotifications(receiverId);
  }

  @Test
  @DisplayName("알림 읽음 처리 요청이 성공하면 204를 반환한다")
  void markAsRead_success() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/notifications/{notificationId}", notificationId)
                .with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isNoContent());

    verify(notificationService).markAsRead(notificationId, receiverId);
  }

  @Test
  @DisplayName("존재하지 않는 알림을 읽음 처리하면 404를 반환한다")
  void markAsRead_notFound_returnsNotFound() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    doThrow(new NotificationNotFoundException())
        .when(notificationService)
        .markAsRead(notificationId, receiverId);

    mockMvc
        .perform(
            delete("/api/notifications/{notificationId}", notificationId)
                .with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("NotificationNotFoundException"));

    verify(notificationService).markAsRead(notificationId, receiverId);
  }

  @Test
  @DisplayName("다른 사용자의 알림을 읽음 처리하면 403을 반환한다")
  void markAsRead_forbidden_returnsForbidden() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    doThrow(new NotificationForbiddenException())
        .when(notificationService)
        .markAsRead(notificationId, receiverId);

    mockMvc
        .perform(
            delete("/api/notifications/{notificationId}", notificationId)
                .with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("NotificationForbiddenException"));

    verify(notificationService).markAsRead(notificationId, receiverId);
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
