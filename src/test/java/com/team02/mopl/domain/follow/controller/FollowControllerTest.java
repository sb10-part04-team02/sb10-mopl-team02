package com.team02.mopl.domain.follow.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.follow.dto.FollowDto;
import com.team02.mopl.domain.follow.dto.FollowRequest;
import com.team02.mopl.domain.follow.exception.CannotFollowSelfException;
import com.team02.mopl.domain.follow.exception.FollowAlreadyExistsException;
import com.team02.mopl.domain.follow.exception.FollowForbiddenException;
import com.team02.mopl.domain.follow.exception.FollowNotFoundException;
import com.team02.mopl.domain.follow.service.FollowService;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FollowController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
public class FollowControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private FollowService followService;

  @Test
  @DisplayName("팔로우 생성 요청이 성공하면 201과 FollowDto를 반환한다")
  void createFollow_success() throws Exception {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    FollowRequest request = new FollowRequest(followeeId);
    FollowDto response = new FollowDto(followId, followeeId, followerId);

    given(followService.createFollow(eq(followerId), any(FollowRequest.class)))
        .willReturn(response);

    mockMvc
        .perform(
            post("/api/follows")
                .with(authentication(authenticationWithPrincipal(followerId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followerId").value(followerId.toString()));

    verify(followService).createFollow(eq(followerId), any(FollowRequest.class));
  }

  @Test
  @DisplayName("팔로우 생성 요청에서 followeeId가 없으면 400을 반환한다")
  void createFollow_invalidRequest_returnsBadRequest() throws Exception {
    FollowRequest request = new FollowRequest(null);

    mockMvc
        .perform(
            post("/api/follows")
                .with(authentication(authenticationWithPrincipal(UUID.randomUUID())))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("MethodArgumentNotValidException"));
  }

  @Test
  @DisplayName("자기 자신 팔로우 시 400을 반환한다")
  void createFollow_selfFollow_returnBadRequest() throws Exception {
    UUID userId = UUID.randomUUID();
    FollowRequest request = new FollowRequest(userId);

    given(followService.createFollow(eq(userId), any(FollowRequest.class)))
        .willThrow(new CannotFollowSelfException());

    mockMvc
        .perform(
            post("/api/follows")
                .with(authentication(authenticationWithPrincipal(userId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("CannotFollowSelfException"));
  }

  @Test
  @DisplayName("이미 팔로우 중이면 400을 반환한다")
  void createFollow_alreadyExists_returnsBadRequest() throws Exception {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    FollowRequest request = new FollowRequest(followeeId);

    given(followService.createFollow(eq(followerId), any(FollowRequest.class)))
        .willThrow(new FollowAlreadyExistsException());

    mockMvc
        .perform(
            post("/api/follows")
                .with(authentication(authenticationWithPrincipal(followerId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("FollowAlreadyExistsException"));
  }

  @Test
  @DisplayName("팔로우 여부 조회가 성공하면 FollowDto를 반환한다")
  void getFollowedByMe_success() throws Exception {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    FollowDto response = new FollowDto(followId, followeeId, followerId);

    given(followService.getFollowedByMe(followerId, followeeId)).willReturn(response);

    mockMvc
        .perform(
            get("/api/follows/followed-by-me")
                .with(authentication(authenticationWithPrincipal(followerId)))
                .param("followeeId", followeeId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(followId.toString()))
        .andExpect(jsonPath("$.followeeId").value(followeeId.toString()))
        .andExpect(jsonPath("$.followerId").value(followerId.toString()));

    verify(followService).getFollowedByMe(followerId, followeeId);
  }

  @Test
  @DisplayName("팔로우 중이 아니면 404를 반환한다")
  void getFollowedByMe_notFound_returnsNotFound() throws Exception {
    UUID followerId = UUID.randomUUID();
    UUID followeeId = UUID.randomUUID();

    given(followService.getFollowedByMe(followerId, followeeId))
        .willThrow(new FollowNotFoundException());

    mockMvc
        .perform(
            get("/api/follows/followed-by-me")
                .with(authentication(authenticationWithPrincipal(followerId)))
                .param("followeeId", followeeId.toString()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"));
  }

  @Test
  @DisplayName("팔로워 수 조회가 성공하면 값을 반환한다")
  void getFollowerCount_success() throws Exception {
    UUID followeeId = UUID.randomUUID();

    given(followService.getFollowerCount(followeeId)).willReturn(3L);

    mockMvc
        .perform(get("/api/follows/count").param("followeeId", followeeId.toString()))
        .andExpect(status().isOk())
        .andExpect(content().string("3"));

    verify(followService).getFollowerCount(followeeId);
  }

  @Test
  @DisplayName("팔로우 취소 요청이 성공하면 204를 반환한다")
  void cancelFollow_success() throws Exception {
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/follows/{followId}", followId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNoContent());

    verify(followService).cancelFollow(followId, requesterId);
  }

  @Test
  @DisplayName("존재하지 않는 팔로우를 취소하면 404를 반환한다")
  void cancelFollow_notFound_returnsNotFound() throws Exception {
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    doThrow(new FollowNotFoundException()).when(followService).cancelFollow(followId, requesterId);

    mockMvc
        .perform(
            delete("/api/follows/{followId}", followId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("FollowNotFoundException"));
  }

  @Test
  @DisplayName("다른 사용자의 팔로우를 취소하면 403을 반환한다")
  void cancelFollow_forbidden_returnsForbidden() throws Exception {
    UUID requesterId = UUID.randomUUID();
    UUID followId = UUID.randomUUID();

    doThrow(new FollowForbiddenException()).when(followService).cancelFollow(followId, requesterId);

    mockMvc
        .perform(
            delete("/api/follows/{followId}", followId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("FollowForbiddenException"));
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
