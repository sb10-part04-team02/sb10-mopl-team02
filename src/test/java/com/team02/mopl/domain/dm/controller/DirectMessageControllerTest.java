package com.team02.mopl.domain.dm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.dm.dto.ConversationDto;
import com.team02.mopl.domain.dm.dto.ConversationSearchRequest;
import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSearchRequest;
import com.team02.mopl.domain.dm.exception.ConversationForbiddenException;
import com.team02.mopl.domain.dm.exception.DirectMessageNotFoundException;
import com.team02.mopl.domain.dm.service.DirectMessageService;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.global.dto.CursorResponse;
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

@WebMvcTest(DirectMessageController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class DirectMessageControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DirectMessageService directMessageService;

  @Test
  @DisplayName("대화 목록 조회 성공 시 200과 CursorResponse를 반환한다")
  void getConversations_success_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID nextIdAfter = UUID.randomUUID();

    ConversationDto conversationDto =
        new ConversationDto(conversationId, new UserSummary(withUserId, "상대방", null), null, false);

    CursorResponse<ConversationDto> response =
        new CursorResponse<>(
            List.of(conversationDto),
            "2026-06-29T00:00:00Z",
            nextIdAfter,
            true,
            5L,
            "CREATED_AT",
            "DESCENDING");

    given(directMessageService.getConversations(eq(userId), any(ConversationSearchRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            get("/api/conversations")
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(conversationId.toString()))
        .andExpect(jsonPath("$.data[0].with.userId").value(withUserId.toString()))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").value("2026-06-29T00:00:00Z"))
        .andExpect(jsonPath("$.nextIdAfter").value(nextIdAfter.toString()))
        .andExpect(jsonPath("$.totalCount").value(5))
        .andExpect(jsonPath("$.sortBy").value("CREATED_AT"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

    verify(directMessageService).getConversations(eq(userId), any(ConversationSearchRequest.class));
  }

  @Test
  @DisplayName("대화 목록 조회 시 cursor만 전달하고 idAfter를 생략하면 400을 반환한다")
  void getConversations_cursorWithoutIdAfter_returns400() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/conversations")
                .queryParam("cursor", "2026-06-30T10:15:30Z")
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("대화 목록이 비어 있으면 200과 빈 data를 반환한다")
  void getConversations_empty_returns200() throws Exception {
    UUID userId = UUID.randomUUID();

    CursorResponse<ConversationDto> response =
        new CursorResponse<>(List.of(), null, null, false, 0L, "CREATED_AT", "DESCENDING");

    given(directMessageService.getConversations(eq(userId), any(ConversationSearchRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            get("/api/conversations")
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data").isEmpty())
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(0));
  }

  @Test
  @DisplayName("상대방 UUID로 대화방 조회 시 userId 파라미터로 200을 반환한다")
  void findConversationWith_success_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID withUserId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    ConversationDto conversationDto =
        new ConversationDto(conversationId, new UserSummary(withUserId, "상대방", null), null, false);

    given(directMessageService.findConversationWith(userId, withUserId))
        .willReturn(conversationDto);

    mockMvc
        .perform(
            get("/api/conversations/with")
                .queryParam("userId", withUserId.toString())
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(conversationId.toString()))
        .andExpect(jsonPath("$.with.userId").value(withUserId.toString()));

    verify(directMessageService).findConversationWith(userId, withUserId);
  }

  @Test
  @DisplayName("상대방 UUID로 대화방 조회 시 userId 파라미터가 누락되면 400을 반환한다")
  void findConversationWith_missingUserId_returns400() throws Exception {
    UUID userId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/conversations/with")
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MissingServletRequestParameterException"));
  }

  @Test
  @DisplayName("DM 목록 조회 성공 시 200과 CursorResponse를 반환한다")
  void getDirectMessages_success_returns200() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID dmId = UUID.randomUUID();
    UUID senderId = UUID.randomUUID();
    UUID receiverId = UUID.randomUUID();

    DirectMessageDto dmDto =
        new DirectMessageDto(
            dmId,
            conversationId,
            Instant.now(),
            new UserSummary(senderId, "발신자", null),
            new UserSummary(receiverId, "수신자", null),
            "안녕하세요");

    CursorResponse<DirectMessageDto> response =
        new CursorResponse<>(List.of(dmDto), null, null, false, 1L, "CREATED_AT", "DESCENDING");

    given(
            directMessageService.getDirectMessages(
                eq(conversationId), eq(userId), any(DirectMessageSearchRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(dmId.toString()))
        .andExpect(jsonPath("$.data[0].content").value("안녕하세요"))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.sortBy").value("CREATED_AT"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));
  }

  @Test
  @DisplayName("cursor만 전달하고 idAfter를 생략하면 400을 반환한다")
  void getDirectMessages_cursorWithoutIdAfter_returns400() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    mockMvc
        .perform(
            get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .queryParam("cursor", "2026-06-30T10:15:30Z")
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("대화방 참여자가 아니면 403을 반환한다")
  void getDirectMessages_notMember_returns403() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();

    given(
            directMessageService.getDirectMessages(
                eq(conversationId), eq(userId), any(DirectMessageSearchRequest.class)))
        .willThrow(new ConversationForbiddenException());

    mockMvc
        .perform(
            get("/api/conversations/{conversationId}/direct-messages", conversationId)
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("ConversationForbiddenException"));
  }

  @Test
  @DisplayName("DM 읽음 처리 성공 시 204를 반환한다")
  void markDirectMessageAsRead_success_returns204() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();

    mockMvc
        .perform(
            post(
                    "/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                    conversationId,
                    directMessageId)
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isNoContent());

    verify(directMessageService).markAsRead(conversationId, directMessageId, userId);
  }

  @Test
  @DisplayName("DM 읽음 처리 시 대화방 참여자가 아니면 403을 반환한다")
  void markDirectMessageAsRead_notMember_returns403() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();

    willThrow(new ConversationForbiddenException())
        .given(directMessageService)
        .markAsRead(conversationId, directMessageId, userId);

    mockMvc
        .perform(
            post(
                    "/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                    conversationId,
                    directMessageId)
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("ConversationForbiddenException"));
  }

  @Test
  @DisplayName("DM 읽음 처리 시 DM을 찾을 수 없으면 404를 반환한다")
  void markDirectMessageAsRead_dmNotFound_returns404() throws Exception {
    UUID userId = UUID.randomUUID();
    UUID conversationId = UUID.randomUUID();
    UUID directMessageId = UUID.randomUUID();

    willThrow(new DirectMessageNotFoundException())
        .given(directMessageService)
        .markAsRead(conversationId, directMessageId, userId);

    mockMvc
        .perform(
            post(
                    "/api/conversations/{conversationId}/direct-messages/{directMessageId}/read",
                    conversationId,
                    directMessageId)
                .with(authentication(new TestingAuthenticationToken(userId, null))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("DirectMessageNotFoundException"));
  }
}
