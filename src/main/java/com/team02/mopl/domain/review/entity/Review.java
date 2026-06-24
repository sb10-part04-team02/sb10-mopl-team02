package com.team02.mopl.domain.review.entity;

import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "reviews",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_reviews_user_content",
            columnNames = {"author_id", "content_id"}))
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
    this.authorId = authorId;
    this.contentId = contentId;
    this.text = text;
    this.rating = rating;
  }
}
