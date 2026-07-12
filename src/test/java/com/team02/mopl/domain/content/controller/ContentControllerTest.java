package com.team02.mopl.domain.content.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.content.dto.ContentCreateRequest;
import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.dto.ContentSearchRequest;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.content.exception.ContentNotFoundException;
import com.team02.mopl.domain.content.exception.InvalidCursorException;
import com.team02.mopl.domain.content.exception.InvalidCursorRequestException;
import com.team02.mopl.domain.content.service.ContentService;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.multipart.MultipartFile;

@WebMvcTest(ContentController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
@WithMockUser(roles = "ADMIN")
class ContentControllerTest {

  @MockitoBean private ContentService contentService;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private ContentDto createContentDto(UUID contentId) {
    return new ContentDto(
        contentId, ContentType.MOVIE, "제목", "설명", "http://img", List.of("태그"), 0.0, 0, 0L);
  }

  private MockMultipartFile createRequestPart(Object request) throws Exception {
    return new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));
  }

  @Nested
  class CreateContent {

    private MockHttpServletRequestBuilder createContentCreateRequest() throws Exception {
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "제목", "설명", List.of("태그"));
      MockMultipartFile thumbnail =
          new MockMultipartFile(
              "thumbnail", "thumbnail.png", MediaType.IMAGE_PNG_VALUE, "image".getBytes());
      return multipart("/api/contents")
          .file(createRequestPart(request))
          .file(thumbnail)
          .with(csrf());
    }

    @Test
    @DisplayName("ADMIN 권한으로 콘텐츠 생성 요청 시 201과 ContentDto를 반환한다")
    void success_shouldReturn201_whenAdminCreatesContent() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentService.create(any(ContentCreateRequest.class), any(MultipartFile.class)))
          .willReturn(createContentDto(contentId));

      // when & then
      mockMvc
          .perform(createContentCreateRequest())
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(contentId.toString()))
          .andExpect(jsonPath("$.title").value("제목"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER 권한으로 콘텐츠 생성 요청 시 403을 반환한다")
    void fail_shouldReturn403_whenUserCreatesContent() throws Exception {
      // when & then
      mockMvc.perform(createContentCreateRequest()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("thumbnail 파트 없이 생성 요청해도 201을 반환한다(required = false)")
    void success_shouldReturn201_whenThumbnailPartMissing() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      ContentCreateRequest request =
          new ContentCreateRequest(ContentType.MOVIE, "제목", "설명", List.of("태그"));
      given(contentService.create(any(ContentCreateRequest.class), isNull()))
          .willReturn(createContentDto(contentId));

      // when & then
      mockMvc
          .perform(multipart("/api/contents").file(createRequestPart(request)).with(csrf()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.id").value(contentId.toString()));

      then(contentService).should().create(any(ContentCreateRequest.class), isNull());
    }

    @Test
    @DisplayName("title이 공백이면 400을 반환하고 서비스를 호출하지 않는다")
    void fail_shouldReturn400_whenTitleBlank() throws Exception {
      // given
      ContentCreateRequest invalid =
          new ContentCreateRequest(ContentType.MOVIE, "  ", "설명", List.of("태그"));

      // when & then
      mockMvc
          .perform(multipart("/api/contents").file(createRequestPart(invalid)).with(csrf()))
          .andExpect(status().isBadRequest());

      then(contentService).shouldHaveNoInteractions();
    }
  }

  @Nested
  class GetContent {

    @Test
    @DisplayName("존재하는 콘텐츠 조회 시 200과 ContentDto를 반환한다")
    void success_shouldReturn200_whenContentExists() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentService.get(contentId)).willReturn(createContentDto(contentId));

      // when & then
      mockMvc
          .perform(get("/api/contents/{contentId}", contentId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(contentId.toString()))
          .andExpect(jsonPath("$.title").value("제목"))
          .andExpect(jsonPath("$.watcherCount").value(0));
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠 조회 시 404와 ContentNotFoundException 에러 포맷을 반환한다")
    void fail_shouldReturn404WithErrorFormat_whenContentNotFound() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentService.get(contentId)).willThrow(new ContentNotFoundException());

      // when & then
      mockMvc
          .perform(get("/api/contents/{contentId}", contentId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("ContentNotFoundException"));
    }
  }

  @Nested
  class GetContents {

    @Test
    @DisplayName("GET /api/contents는 쿼리 파라미터를 바인딩해 서비스를 호출하고 CursorResponse를 직렬화한다")
    void success_shouldReturnCursorResponse_whenParamsBound() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      UUID nextIdAfter = UUID.randomUUID();
      CursorResponse<ContentDto> response =
          new CursorResponse<>(
              List.of(createContentDto(contentId)),
              "5",
              nextIdAfter,
              true,
              10L,
              "WATCHER_COUNT",
              "DESCENDING");
      given(contentService.getContents(any(ContentSearchRequest.class))).willReturn(response);

      // when & then
      mockMvc
          .perform(
              get("/api/contents")
                  .param("limit", "20")
                  .param("sortBy", "WATCHER_COUNT")
                  .param("sortDirection", "DESCENDING"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data[0].id").value(contentId.toString()))
          .andExpect(jsonPath("$.data[0].title").value("제목"))
          .andExpect(jsonPath("$.data[0].watcherCount").value(0))
          .andExpect(jsonPath("$.nextCursor").value("5"))
          .andExpect(jsonPath("$.nextIdAfter").value(nextIdAfter.toString()))
          .andExpect(jsonPath("$.hasNext").value(true))
          .andExpect(jsonPath("$.totalCount").value(10))
          .andExpect(jsonPath("$.sortBy").value("WATCHER_COUNT"))
          .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

      then(contentService).should().getContents(any(ContentSearchRequest.class));
    }

    @Test
    @DisplayName("cursor만 있고 idAfter가 없는 목록 조회 시 400과 InvalidCursorRequestException 에러 포맷을 반환한다")
    void fail_shouldReturn400WithErrorFormat_whenHalfCursorGiven() throws Exception {
      // given
      given(contentService.getContents(any(ContentSearchRequest.class)))
          .willThrow(new InvalidCursorRequestException());

      // when & then
      mockMvc
          .perform(get("/api/contents").param("cursor", "somecursor"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("InvalidCursorRequestException"))
          .andExpect(jsonPath("$.details.cursor").exists());
    }

    @Test
    @DisplayName("서비스가 InvalidCursorException을 던지면 400과 예외명을 응답한다")
    void fail_shouldReturn400_whenInvalidCursor() throws Exception {
      // given
      given(contentService.getContents(any(ContentSearchRequest.class)))
          .willThrow(new InvalidCursorException(SortBy.RATE, "abc"));

      // when & then
      mockMvc
          .perform(
              get("/api/contents")
                  .param("limit", "20")
                  .param("cursor", "abc")
                  .param("sortBy", "RATE"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.exceptionName").value("InvalidCursorException"))
          .andExpect(jsonPath("$.details.cursor").exists());
    }
  }

  @Nested
  class UpdateContent {

    private MockHttpServletRequestBuilder createContentUpdateRequest(UUID contentId)
        throws Exception {
      ContentUpdateRequest request = new ContentUpdateRequest("수정 제목", "수정 설명", List.of("태그"));
      return multipart("/api/contents/{contentId}", contentId)
          .file(createRequestPart(request))
          .with(
              servletRequest -> {
                servletRequest.setMethod("PATCH");
                return servletRequest;
              })
          .with(csrf());
    }

    @Test
    @DisplayName("ADMIN 권한으로 콘텐츠 수정 요청 시 200과 ContentDto를 반환한다")
    void success_shouldReturn200_whenAdminUpdatesContent() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentService.update(eq(contentId), any(ContentUpdateRequest.class), isNull()))
          .willReturn(createContentDto(contentId));

      // when & then
      mockMvc
          .perform(createContentUpdateRequest(contentId))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(contentId.toString()));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER 권한으로 콘텐츠 수정 요청 시 403을 반환한다")
    void fail_shouldReturn403_whenUserUpdatesContent() throws Exception {
      // when & then
      mockMvc
          .perform(createContentUpdateRequest(UUID.randomUUID()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠 수정 시 404와 ContentNotFoundException 에러 포맷을 반환한다")
    void fail_shouldReturn404_whenContentNotFound() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      given(contentService.update(eq(contentId), any(ContentUpdateRequest.class), isNull()))
          .willThrow(new ContentNotFoundException());

      // when & then
      mockMvc
          .perform(createContentUpdateRequest(contentId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("ContentNotFoundException"));
    }

    @Test
    @DisplayName("title이 100자를 넘으면 400을 반환하고 서비스를 호출하지 않는다")
    void fail_shouldReturn400_whenTitleTooLong() throws Exception {
      // given
      ContentUpdateRequest invalid = new ContentUpdateRequest("a".repeat(101), null, null);

      // when & then
      mockMvc
          .perform(
              multipart("/api/contents/{contentId}", UUID.randomUUID())
                  .file(createRequestPart(invalid))
                  .with(
                      servletRequest -> {
                        servletRequest.setMethod("PATCH");
                        return servletRequest;
                      })
                  .with(csrf()))
          .andExpect(status().isBadRequest());

      then(contentService).shouldHaveNoInteractions();
    }
  }

  @Nested
  class DeleteContent {

    @Test
    @DisplayName("ADMIN 권한으로 콘텐츠 삭제 요청 시 204를 반환한다")
    void success_shouldReturn204_whenAdminDeletesContent() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();

      // when & then
      mockMvc
          .perform(delete("/api/contents/{contentId}", contentId))
          .andExpect(status().isNoContent());

      then(contentService).should().delete(contentId);
    }

    @Test
    @DisplayName("존재하지 않는 콘텐츠 삭제 시 404와 ContentNotFoundException 에러 포맷을 반환한다")
    void fail_shouldReturn404_whenContentNotFound() throws Exception {
      // given
      UUID contentId = UUID.randomUUID();
      willThrow(new ContentNotFoundException()).given(contentService).delete(contentId);

      // when & then
      mockMvc
          .perform(delete("/api/contents/{contentId}", contentId))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.exceptionName").value("ContentNotFoundException"));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("USER 권한으로 콘텐츠 삭제 요청 시 403을 반환한다")
    void fail_shouldReturn403_whenUserDeletesContent() throws Exception {
      // when & then
      mockMvc
          .perform(delete("/api/contents/{contentId}", UUID.randomUUID()))
          .andExpect(status().isForbidden());
    }
  }
}
