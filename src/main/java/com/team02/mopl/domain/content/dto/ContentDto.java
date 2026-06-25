package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.content.enums.ContentType;
import java.util.List;
import java.util.UUID;

public record ContentDto(
    UUID id,
    ContentType type,
    String title,
    String description,
    String thumbnailUrl,
    List<String> tags,
    double averageRating,
    int reviewCount,
    long watcherCount) {

  public static ContentDto from(Content content) {
    return new ContentDto(
        content.getId(),
        content.getContentType(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        content.getTags().stream().map(Tag::getName).toList(),
        content.getAverageRating(),
        content.getReviewCount(),
        0); // TODO : Content 엔티티에 watcherCount 부재 — watching 도메인 연동 후 실제 값 매핑
  }
}
