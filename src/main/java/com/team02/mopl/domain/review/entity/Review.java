package com.team02.mopl.domain.review.entity;

import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "reviews")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseMutableEntity {

  @Column(name = "author_id", nullable = false)
  private UUID authorId;

  @Column(name = "content_id", nullable = false)
  private UUID contentId;

  @Column(name = "text", nullable = false, columnDefinition = "TEXT")
  private String text;

  @Column(nullable = false)
  private double rating = 0.0;

  public Review(UUID authorId, UUID contentId, String text, double rating) {
    this.authorId = Objects.requireNonNull(authorId, "authorId는 null일 수 없습니다.");
    this.contentId = Objects.requireNonNull(contentId, "contentId는 null일 수 없습니다.");
    this.text = Objects.requireNonNull(text, "text는 null일 수 없습니다.");

    validateRating(rating);
    this.rating = rating;
  }

  public void update(String text, Double rating) {
    if (text != null) {
      this.text = text;
    }
    if (rating != null) {
      validateRating(rating);
      this.rating = rating;
    }
  }

  private static void validateRating(double rating) {
    if (rating < 0.0 || rating > 5.0) {
      throw new IllegalArgumentException("rating은 0.0 이상 5.0 이하이어야 합니다.");
    }
  }
}
