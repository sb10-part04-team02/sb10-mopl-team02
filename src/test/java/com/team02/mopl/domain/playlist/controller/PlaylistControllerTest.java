package com.team02.mopl.domain.playlist.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.playlist.exception.PlaylistContentAlreadyExistsException;
import com.team02.mopl.domain.playlist.exception.PlaylistContentNotFoundException;
import com.team02.mopl.domain.playlist.exception.PlaylistForbiddenException;
import com.team02.mopl.domain.playlist.service.PlaylistService;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PlaylistController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class PlaylistControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private PlaylistService playlistService;

  @Test
  @DisplayName("콘텐츠 추가가 성공하면 204를 반환하고 경로변수·요청자를 서비스로 전달한다")
  void addContent_success() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNoContent());

    verify(playlistService).addContent(playlistId, requesterId, contentId);
  }

  @Test
  @DisplayName("소유자가 아니면 403을 반환한다")
  void addContent_forbidden_returnsForbidden() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    doThrow(new PlaylistForbiddenException())
        .when(playlistService)
        .addContent(playlistId, requesterId, contentId);

    mockMvc
        .perform(
            post("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.exceptionName").value("PlaylistForbiddenException"));
  }

  @Test
  @DisplayName("콘텐츠가 없으면 404를 반환한다")
  void addContent_contentNotFound_returnsNotFound() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    doThrow(new BusinessException(ErrorCode.CONTENT_NOT_FOUND))
        .when(playlistService)
        .addContent(playlistId, requesterId, contentId);

    mockMvc
        .perform(
            post("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("이미 추가된 콘텐츠면 400을 반환한다")
  void addContent_alreadyExists_returnsBadRequest() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    doThrow(new PlaylistContentAlreadyExistsException())
        .when(playlistService)
        .addContent(playlistId, requesterId, contentId);

    mockMvc
        .perform(
            post("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("PlaylistContentAlreadyExistsException"));
  }

  @Test
  @DisplayName("콘텐츠 삭제가 성공하면 204를 반환하고 경로변수·요청자를 서비스로 전달한다")
  void removeContent_success() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNoContent());

    verify(playlistService).removeContent(playlistId, requesterId, contentId);
  }

  @Test
  @DisplayName("플레이리스트에 포함되지 않은 콘텐츠면 404를 반환한다")
  void removeContent_notFound_returnsNotFound() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    doThrow(new PlaylistContentNotFoundException())
        .when(playlistService)
        .removeContent(playlistId, requesterId, contentId);

    mockMvc
        .perform(
            delete("/api/playlists/{playlistId}/contents/{contentId}", playlistId, contentId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.exceptionName").value("PlaylistContentNotFoundException"));
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
