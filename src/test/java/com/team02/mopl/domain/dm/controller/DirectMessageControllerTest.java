package com.team02.mopl.domain.dm.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.dm.dto.DirectMessageDto;
import com.team02.mopl.domain.dm.dto.DirectMessageSearchRequest;
import com.team02.mopl.domain.dm.exception.ConversationForbiddenException;
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
}
