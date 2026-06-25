package com.team02.mopl.domain.review.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReviewRequestValidationTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setUp() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void tearDown() {
    factory.close();
  }

  @Nested
  @DisplayName("ReviewCreateRequest 검증")
  class CreateRequest {

    @Test
    @DisplayName("모든 필드가 유효하면(rating 경계값 포함) 위반이 없다")
    void valid_noViolations() {
      ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "재밌어요", 5.0);

      Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);

      assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @DisplayName("text가 null이거나 공백이면 위반이 발생한다")
    @ValueSource(strings = {"", " ", "   "})
    void blankText_hasViolation(String text) {
      ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), text, 4.0);

      Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);

      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("text"));
    }

    @Test
    @DisplayName("text가 500자를 초과하면 위반이 발생한다")
    void tooLongText_hasViolation() {
      ReviewCreateRequest request =
          new ReviewCreateRequest(UUID.randomUUID(), "가".repeat(501), 4.0);

      Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);

      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("text"));
    }

    @ParameterizedTest
    @DisplayName("rating이 0.0~5.0 범위를 벗어나면 위반이 발생한다")
    @ValueSource(doubles = {-0.1, 5.1})
    void ratingOutOfRange_hasViolation(double rating) {
      ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "내용", rating);

      Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);

      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rating"));
    }

    @Test
    @DisplayName("contentId가 null이면 위반이 발생한다")
    void nullContentId_hasViolation() {
      ReviewCreateRequest request = new ReviewCreateRequest(null, "내용", 4.0);

      Set<ConstraintViolation<ReviewCreateRequest>> violations = validator.validate(request);

      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("contentId"));
    }
  }

  @Nested
  @DisplayName("ReviewUpdateRequest 검증")
  class UpdateRequest {

    @Test
    @DisplayName("text와 rating이 모두 null이면(부분 수정) 위반이 없다")
    void allNull_noViolations() {
      ReviewUpdateRequest request = new ReviewUpdateRequest(null, null);

      Set<ConstraintViolation<ReviewUpdateRequest>> violations = validator.validate(request);

      assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("text가 500자를 초과하면 위반이 발생한다")
    void tooLongText_hasViolation() {
      ReviewUpdateRequest request = new ReviewUpdateRequest("가".repeat(501), null);

      Set<ConstraintViolation<ReviewUpdateRequest>> violations = validator.validate(request);

      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("text"));
    }

    @ParameterizedTest
    @DisplayName("rating이 0.0~5.0 범위를 벗어나면 위반이 발생한다")
    @ValueSource(doubles = {-0.1, 5.1})
    void ratingOutOfRange_hasViolation(double rating) {
      ReviewUpdateRequest request = new ReviewUpdateRequest(null, rating);

      Set<ConstraintViolation<ReviewUpdateRequest>> violations = validator.validate(request);

      assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("rating"));
    }
  }
}
