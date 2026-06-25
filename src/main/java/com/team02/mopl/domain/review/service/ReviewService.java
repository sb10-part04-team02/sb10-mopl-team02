package com.team02.mopl.domain.review.service;

import com.team02.mopl.domain.review.dto.ReviewCreateRequest;
import com.team02.mopl.domain.review.dto.ReviewDto;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.exception.ReviewAlreadyExistsException;
import com.team02.mopl.domain.review.mapper.ReviewMapper;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewService {

  private final ReviewRepository reviewRepository;
  private final ReviewMapper reviewMapper;

  @Transactional
  public ReviewDto createReview(UUID authorId, ReviewCreateRequest request) {
    log.debug("리뷰 생성 시작: authorId={}, contentId={}", authorId, request.contentId());

    if (reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(
        authorId, request.contentId())) {
      throw new ReviewAlreadyExistsException();
    }

    Review review = new Review(authorId, request.contentId(), request.text(), request.rating());

    // 광클이나 동시 요청 대응으로의 2차 체크
    Review savedReview;
    try {
      savedReview = reviewRepository.saveAndFlush(review);
    } catch (DataIntegrityViolationException e) {
      log.warn("리뷰 저장 중 무결성 위반 발생: authorId={}, contentId={}", authorId, request.contentId(), e);
      throw new ReviewAlreadyExistsException();
    }
    ReviewDto reviewDto = reviewMapper.toDto(savedReview);

    log.info("리뷰 생성 성공: reviewId={}, authorId={}", reviewDto.id(), authorId);
    return reviewDto;
  }
}
