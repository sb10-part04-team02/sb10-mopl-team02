package com.team02.mopl.domain.content.ingestion.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentIngestionTrigger;
import com.team02.mopl.domain.content.ingestion.dto.IngestionTriggerRequest;
import com.team02.mopl.domain.content.ingestion.exception.IngestionAlreadyRunningException;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(ContentIngestionController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class ContentIngestionControllerTest {

  @MockitoBean private ContentIngestionTrigger contentIngestionTrigger;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private MockHttpServletRequestBuilder triggerRequest(Set<ContentSource> sources)
      throws Exception {
    return post("/api/contents/ingestion")
        .contentType(MediaType.APPLICATION_JSON)
        .content(objectMapper.writeValueAsString(new IngestionTriggerRequest(sources)));
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("어드민이 소스를 지정해 수집을 요청하면 202와 시작된 소스를 응답한다")
  void triggerIngestion_asAdmin_returnsAccepted() throws Exception {
    given(contentIngestionTrigger.trigger(any())).willReturn(EnumSet.of(ContentSource.TMDB));

    mockMvc
        .perform(triggerRequest(Set.of(ContentSource.TMDB)))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.sources[0]").value("TMDB"))
        .andExpect(jsonPath("$.message").isNotEmpty());
  }

  @Test
  @WithMockUser(roles = "USER")
  @DisplayName("일반 사용자는 수동 수집을 요청할 수 없다")
  void triggerIngestion_asUser_returnsForbidden() throws Exception {
    mockMvc.perform(triggerRequest(Set.of(ContentSource.TMDB))).andExpect(status().isForbidden());

    then(contentIngestionTrigger).shouldHaveNoInteractions();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("sources에 null이 섞여 있으면 400으로 막는다")
  void triggerIngestion_withNullSource_returnsBadRequest() throws Exception {
    // given
    mockMvc
        .perform(
            post("/api/contents/ingestion")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sources\":[null]}"))
        .andExpect(status().isBadRequest());

    // then
    then(contentIngestionTrigger).shouldHaveNoInteractions();
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("수집이 이미 실행 중이면 409를 응답한다")
  void triggerIngestion_whenAlreadyRunning_returnsConflict() throws Exception {
    given(contentIngestionTrigger.trigger(any())).willThrow(new IngestionAlreadyRunningException());

    mockMvc.perform(triggerRequest(Set.of(ContentSource.TMDB))).andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = "ADMIN")
  @DisplayName("본문 없이 요청하면 전체 소스 수집으로 처리한다")
  void triggerIngestion_withoutBody_triggersAllSources() throws Exception {
    given(contentIngestionTrigger.trigger(any())).willReturn(EnumSet.allOf(ContentSource.class));

    mockMvc
        .perform(post("/api/contents/ingestion").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isAccepted());

    then(contentIngestionTrigger).should().trigger(null);
  }
}
