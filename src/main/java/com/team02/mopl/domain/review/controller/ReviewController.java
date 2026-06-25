package com.team02.mopl.domain.review.controller;

import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.service.ReviewService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
public class ReviewController implements ReviewApi {

  private final ReviewService reviewService;

  @PostMapping
  public ResponseEntity<ReviewDto> createReview(
      @AuthenticationPrincipal UUID authorId, @RequestBody @Valid ReviewCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(reviewService.createReview(authorId, request));
  }
}
