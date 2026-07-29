package com.team02.mopl.domain.review.controller;

import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.dto.ReviewSearchRequest;
import com.team02.mopl.domain.review.dto.ReviewUpdateRequest;
import com.team02.mopl.domain.review.service.ReviewService;
import com.team02.mopl.global.dto.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController implements ReviewApi {

  private final ReviewService reviewService;

  @GetMapping
  public ResponseEntity<CursorResponse<ReviewDto>> getReviews(
      @ParameterObject @ModelAttribute @Valid ReviewSearchRequest request) {
    return ResponseEntity.ok(reviewService.getReviews(request));
  }

  @PostMapping
  public ResponseEntity<ReviewDto> createReview(
      @AuthenticationPrincipal UUID authorId, @RequestBody @Valid ReviewCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(reviewService.createReview(authorId, request));
  }

  @PatchMapping("/{reviewId}")
  public ResponseEntity<ReviewDto> updateReview(
      @PathVariable UUID reviewId,
      @AuthenticationPrincipal UUID requesterId,
      @RequestBody @Valid ReviewUpdateRequest request) {
    return ResponseEntity.ok(reviewService.updateReview(reviewId, requesterId, request));
  }

  @DeleteMapping("/{reviewId}")
  public ResponseEntity<Void> deleteReview(
      @PathVariable UUID reviewId, @AuthenticationPrincipal UUID requesterId) {
    reviewService.deleteReview(reviewId, requesterId);
    return ResponseEntity.noContent().build();
  }
}
