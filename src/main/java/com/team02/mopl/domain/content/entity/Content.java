package com.team02.mopl.domain.content.entity;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.global.entity.BaseMutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Content extends BaseMutableEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "content_type", nullable = false, length = 20)
  private ContentType contentType;

  @Column(nullable = false, length = 100)
  private String title;

  @Column(nullable = false, length = 255)
  private String description;

  @Column(name = "thumbnail_url", nullable = false, columnDefinition = "TEXT")
  private String thumbnailUrl;

  @Column(name = "average_rating", nullable = false)
  private double averageRating = 0.0;

  @Column(name = "review_count", nullable = false)
  private int reviewCount = 0;

  @OneToMany(mappedBy = "content", fetch = FetchType.LAZY)
  private List<Tag> tags = new ArrayList<>();

  public Content(ContentType contentType, String title, String description, String thumbnailUrl) {
    this.contentType = Objects.requireNonNull(contentType, "contentType은 null일 수 없습니다.");
    this.title = validateNotBlank(title, "title");
    this.description = validateNotBlank(description, "description");
    this.thumbnailUrl = validateNotBlank(thumbnailUrl, "thumbnailUrl");
  }

  public void update(String title, String description) {
    if (StringUtils.hasText(title)) {
      this.title = title;
    }
    if (StringUtils.hasText(description)) {
      this.description = description;
    }
  }

  public void changeThumbnailUrl(String thumbnailUrl) {
    if (StringUtils.hasText(thumbnailUrl)) {
      this.thumbnailUrl = thumbnailUrl;
    }
  }

  private static String validateNotBlank(String value, String field) {
    Objects.requireNonNull(value, field + "은(는) null일 수 없습니다.");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + "은(는) 공백일 수 없습니다.");
    }
    return value;
  }
}
