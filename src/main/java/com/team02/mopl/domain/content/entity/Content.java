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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
    this.contentType = contentType;
    this.title = title;
    this.description = description;
    this.thumbnailUrl = thumbnailUrl;
  }
}
