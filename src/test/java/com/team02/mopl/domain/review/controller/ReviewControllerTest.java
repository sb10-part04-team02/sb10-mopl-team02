package com.team02.mopl.domain.review.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.dto.ReviewSearchRequest;
import com.team02.mopl.domain.review.dto.ReviewUpdateRequest;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.domain.review.exception.ReviewAlreadyExistsException;
import com.team02.mopl.domain.review.service.ReviewService;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.global.exception.GlobalExceptionHandler;
import com.team02.mopl.support.TestSecurityConfiguration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
@Import({TestSecurityConfiguration.class, GlobalExceptionHandler.class})
class ReviewControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private ReviewService reviewService;

  private ReviewDto reviewDto(UUID id, UUID contentId, UUID authorId) {
    return new ReviewDto(id, contentId, new UserSummary(authorId, "작성자", null), "재밌어요", 4.5);
  }

  @Test
  @DisplayName("리뷰 목록 조회가 성공하면 200과 커서 응답을 반환한다")
  void getReviews_success() throws Exception {
    // given
    UUID contentId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    UUID authorId = UUID.randomUUID();

    CursorResponse<ReviewDto> response =
        new CursorResponse<>(
            List.of(reviewDto(reviewId, contentId, authorId)),
            null,
            null,
            false,
            1L,
            "CREATED_AT",
            "DESCENDING");
    given(reviewService.getReviews(any(ReviewSearchRequest.class))).willReturn(response);

    // when & then
    mockMvc
        .perform(get("/api/reviews").param("contentId", contentId.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(reviewId.toString()))
        .andExpect(jsonPath("$.data[0].contentId").value(contentId.toString()))
        .andExpect(jsonPath("$.data[0].rating").value(4.5))
        .andExpect(jsonPath("$.hasNext").value(false))
        .andExpect(jsonPath("$.totalCount").value(1))
        .andExpect(jsonPath("$.sortBy").value("CREATED_AT"))
        .andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

    verify(reviewService).getReviews(any(ReviewSearchRequest.class));
  }

  @Test
  @DisplayName("커서 페이지네이션 파라미터가 ReviewSearchRequest로 그대로 바인딩된다")
  void getReviews_bindsPaginationParams() throws Exception {
    // given
    UUID contentId = UUID.randomUUID();
    UUID idAfter = UUID.randomUUID();
    given(reviewService.getReviews(any(ReviewSearchRequest.class)))
        .willReturn(new CursorResponse<>(List.of(), null, null, false, 0L, "RATING", "ASCENDING"));

    // when
    mockMvc
        .perform(
            get("/api/reviews")
                .param("contentId", contentId.toString())
                .param("cursor", "4.0")
                .param("idAfter", idAfter.toString())
                .param("limit", "20")
                .param("sortBy", "RATING")
                .param("sortDirection", "ASCENDING"))
        .andExpect(status().isOk());

    // then
    ArgumentCaptor<ReviewSearchRequest> captor = ArgumentCaptor.forClass(ReviewSearchRequest.class);
    verify(reviewService).getReviews(captor.capture());
    ReviewSearchRequest bound = captor.getValue();
    assertThat(bound.contentId()).isEqualTo(contentId);
    assertThat(bound.cursor()).isEqualTo("4.0");
    assertThat(bound.idAfter()).isEqualTo(idAfter);
    assertThat(bound.limit()).isEqualTo(20);
    assertThat(bound.sortBy()).isEqualTo(ReviewSortBy.RATING);
    assertThat(bound.sortDirection()).isEqualTo(SortDirection.ASCENDING);
  }

  @Test
  @DisplayName("리뷰 생성이 성공하면 201과 생성된 리뷰를 반환한다")
  void createReview_success() throws Exception {
    // given
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4.5);

    given(reviewService.createReview(eq(authorId), any(ReviewCreateRequest.class)))
        .willReturn(reviewDto(reviewId, contentId, authorId));

    // when & then
    mockMvc
        .perform(
            post("/api/reviews")
                .with(authentication(authenticationWithPrincipal(authorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(reviewId.toString()))
        .andExpect(jsonPath("$.contentId").value(contentId.toString()));

    verify(reviewService).createReview(eq(authorId), any(ReviewCreateRequest.class));
  }

  @Test
  @DisplayName("필수 필드가 누락된 리뷰 생성 요청이면 400을 반환한다")
  void createReview_invalidRequest_returnsBadRequest() throws Exception {
    // given
    UUID authorId = UUID.randomUUID();
    // contentId 누락(null)으로 @NotNull 검증 실패
    String body = "{\"text\":\"재밌어요\",\"rating\":4.5}";

    // when & then
    mockMvc
        .perform(
            post("/api/reviews")
                .with(authentication(authenticationWithPrincipal(authorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("이미 작성한 리뷰가 존재하면 409를 반환한다")
  void createReview_alreadyExists_returnsConflict() throws Exception {
    // given
    UUID authorId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", 4.5);

    given(reviewService.createReview(eq(authorId), any(ReviewCreateRequest.class)))
        .willThrow(new ReviewAlreadyExistsException());

    // when & then
    mockMvc
        .perform(
            post("/api/reviews")
                .with(authentication(authenticationWithPrincipal(authorId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.exceptionName").value("ReviewAlreadyExistsException"));
  }

  @Test
  @DisplayName("리뷰 수정이 성공하면 200과 수정된 리뷰를 반환한다")
  void updateReview_success() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID contentId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 내용", 2.0);

    given(reviewService.updateReview(eq(reviewId), eq(requesterId), any(ReviewUpdateRequest.class)))
        .willReturn(
            new ReviewDto(
                reviewId, contentId, new UserSummary(requesterId, "작성자", null), "수정된 내용", 2.0));

    // when & then
    mockMvc
        .perform(
            patch("/api/reviews/{reviewId}", reviewId)
                .with(authentication(authenticationWithPrincipal(requesterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(reviewId.toString()))
        .andExpect(jsonPath("$.text").value("수정된 내용"))
        .andExpect(jsonPath("$.rating").value(2.0));

    verify(reviewService)
        .updateReview(eq(reviewId), eq(requesterId), any(ReviewUpdateRequest.class));
  }

  @Test
  @DisplayName("작성자가 아닌 사용자가 리뷰를 수정하면 403을 반환한다")
  void updateReview_forbidden_returnsForbidden() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 내용", 2.0);

    given(reviewService.updateReview(eq(reviewId), eq(requesterId), any(ReviewUpdateRequest.class)))
        .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

    // when & then
    mockMvc
        .perform(
            patch("/api/reviews/{reviewId}", reviewId)
                .with(authentication(authenticationWithPrincipal(requesterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("존재하지 않는 리뷰를 수정하면 404를 반환한다")
  void updateReview_notFound_returnsNotFound() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();
    ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 내용", 2.0);

    given(reviewService.updateReview(eq(reviewId), eq(requesterId), any(ReviewUpdateRequest.class)))
        .willThrow(new BusinessException(ErrorCode.REVIEW_NOT_FOUND));

    // when & then
    mockMvc
        .perform(
            patch("/api/reviews/{reviewId}", reviewId)
                .with(authentication(authenticationWithPrincipal(requesterId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("리뷰 삭제가 성공하면 204를 반환한다")
  void deleteReview_success() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    // when & then
    mockMvc
        .perform(
            delete("/api/reviews/{reviewId}", reviewId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNoContent());

    verify(reviewService).deleteReview(reviewId, requesterId);
  }

  @Test
  @DisplayName("작성자가 아닌 사용자가 리뷰를 삭제하면 403을 반환한다")
  void deleteReview_forbidden_returnsForbidden() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    doThrow(new BusinessException(ErrorCode.FORBIDDEN))
        .when(reviewService)
        .deleteReview(reviewId, requesterId);

    // when & then
    mockMvc
        .perform(
            delete("/api/reviews/{reviewId}", reviewId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("존재하지 않는 리뷰를 삭제하면 404를 반환한다")
  void deleteReview_notFound_returnsNotFound() throws Exception {
    // given
    UUID requesterId = UUID.randomUUID();
    UUID reviewId = UUID.randomUUID();

    doThrow(new BusinessException(ErrorCode.REVIEW_NOT_FOUND))
        .when(reviewService)
        .deleteReview(reviewId, requesterId);

    // when & then
    mockMvc
        .perform(
            delete("/api/reviews/{reviewId}", reviewId)
                .with(authentication(authenticationWithPrincipal(requesterId))))
        .andExpect(status().isNotFound());
  }

  private TestingAuthenticationToken authenticationWithPrincipal(UUID principal) {
    return new TestingAuthenticationToken(principal, null);
  }
}
