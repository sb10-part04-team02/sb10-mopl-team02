package com.team02.mopl.domain.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.dto.NotificationSearchRequest;
import com.team02.mopl.domain.notification.entity.enums.NotificationLevel;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.domain.notification.exception.NotificationForbiddenException;
import com.team02.mopl.domain.notification.exception.NotificationNotFoundException;
import com.team02.mopl.domain.notification.service.NotificationService;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
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
  @DisplayName("알림 목록 조회가 성공하면 200과 커서 응답을 반환한다")
  void getNotifications_success() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();
    UUID nextIdAfter = UUID.randomUUID();

    NotificationDto notification =
        new NotificationDto(
            notificationId,
            Instant.parse("2026-06-29T00:00:00Z"),
            receiverId,
            "알림 제목",
            "알림 내용",
            NotificationLevel.INFO);

    CursorResponse<NotificationDto> response =
        new CursorResponse<>(
            List.of(notification),
            "2026-06-29T00:00:00Z",
            nextIdAfter,
            true,
            3L,
            NotificationSortBy.CREATED_AT.name(),
            SortDirection.DESCENDING.name());

    given(
            notificationService.getNotifications(
                eq(receiverId), any(NotificationSearchRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            get("/api/notifications").with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(notificationId.toString()))
        .andExpect(jsonPath("$.data[0].receiverId").value(receiverId.toString()))
        .andExpect(jsonPath("$.data[0].title").value("알림 제목"))
        .andExpect(jsonPath("$.data[0].content").value("알림 내용"))
        .andExpect(jsonPath("$.data[0].level").value("INFO"))
        .andExpect(jsonPath("$.nextCursor").value("2026-06-29T00:00:00Z"))
        .andExpect(jsonPath("$.nextIdAfter").value(nextIdAfter.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.totalCount").value(3))
        .andExpect(jsonPath("$.sortBy").value("CREATED_AT"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

    verify(notificationService)
        .getNotifications(eq(receiverId), any(NotificationSearchRequest.class));
  }

  @Test
  @DisplayName("알림 목록이 비어 있으면 200과 빈 data를 반환한다")
  void getNotifications_empty_success() throws Exception {
    UUID receiverId = UUID.randomUUID();

    CursorResponse<NotificationDto> response =
        new CursorResponse<>(
            List.of(),
            null,
            null,
            false,
            0L,
            NotificationSortBy.CREATED_AT.name(),
            SortDirection.DESCENDING.name());

    given(
            notificationService.getNotifications(
                eq(receiverId), any(NotificationSearchRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            get("/api/notifications").with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.nextCursor").doesNotExist())
        .andExpect(jsonPath("$.nextIdAfter").doesNotExist())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(0));

    verify(notificationService)
        .getNotifications(eq(receiverId), any(NotificationSearchRequest.class));
  }

  @Test
  @DisplayName("알림 목록 조회 시 커서 페이지네이션 파라미터를 전달할 수 있다")
  void getNotifications_withCursorParams_success() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();

    CursorResponse<NotificationDto> response =
        new CursorResponse<>(
            List.of(),
            null,
            null,
            false,
            0L,
            NotificationSortBy.CREATED_AT.name(),
            SortDirection.ASCENDING.name());

    given(
            notificationService.getNotifications(
                eq(receiverId), any(NotificationSearchRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            get("/api/notifications")
                .param("cursor", "2026-06-29T00:00:00Z")
                .param("idAfter", idAfter.toString())
                .param("limit", "10")
                .param("sortDirection", "ASCENDING")
                .param("sortBy", "CREATED_AT")
                .with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.sortBy").value("CREATED_AT"))
        .andExpect(jsonPath("$.sortDirection").value("ASCENDING"));

    verify(notificationService)
        .getNotifications(eq(receiverId), any(NotificationSearchRequest.class));
  }

  @Test
  @DisplayName("알림 읽음 처리 요청이 성공하면 204를 반환한다")
  void markAsRead_success() throws Exception {
    UUID receiverId = UUID.randomUUID();
    UUID notificationId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/notifications/{notificationId}", notificationId)
                .with(csrf())
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
                .with(csrf())
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
                .with(csrf())
                .with(authentication(authenticationWithPrincipal(receiverId))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("NotificationForbiddenException"));

    verify(notificationService).markAsRead(notificationId, receiverId);
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
