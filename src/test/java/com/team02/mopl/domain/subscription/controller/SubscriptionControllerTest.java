package com.team02.mopl.domain.subscription.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team02.mopl.domain.subscription.exception.SubscriptionAlreadyExistsException;
import com.team02.mopl.domain.subscription.service.SubscriptionService;
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

@WebMvcTest(SubscriptionController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class SubscriptionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private SubscriptionService subscriptionService;

  @Test
  @DisplayName("구독 요청이 성공하면 204를 반환한다")
  void subscribe_success() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    mockMvc
        .perform(
            post("/api/playlists/{playlistId}/subscription", playlistId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNoContent());

    verify(subscriptionService).subscribe(playlistId, requesterId);
  }

  @Test
  @DisplayName("이미 구독 중이면 400을 반환한다")
  void subscribe_alreadyExists_returnsBadRequest() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    doThrow(new SubscriptionAlreadyExistsException())
        .when(subscriptionService)
        .subscribe(playlistId, requesterId);

    mockMvc
        .perform(
            post("/api/playlists/{playlistId}/subscription", playlistId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.exceptionName").value("SubscriptionAlreadyExistsException"));
  }

  @Test
  @DisplayName("구독 취소 요청이 성공하면 204를 반환한다")
  void unsubscribe_success() throws Exception {
    UUID playlistId = UUID.randomUUID();
    UUID requesterId = UUID.randomUUID();

    mockMvc
        .perform(
            delete("/api/playlists/{playlistId}/subscription", playlistId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNoContent());

    verify(subscriptionService).unsubscribe(playlistId, requesterId);
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
