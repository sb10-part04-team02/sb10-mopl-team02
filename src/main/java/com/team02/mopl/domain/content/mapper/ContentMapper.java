package com.team02.mopl.domain.content.mapper;

import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import java.util.List;
import org.springframework.stereotype.Component;

// TODO: 추후 MapStruct로 변경
@Component
public class ContentMapper {

  public ContentDto toDto(Content content, List<Tag> tags, long watcherCount) {
    return new ContentDto(
        content.getId(),
        content.getContentType(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        toTagNames(tags),
        content.getAverageRating(),
        content.getReviewCount(),
        watcherCount);
  }

  private List<String> toTagNames(List<Tag> tags) {
    if (tags == null) {
      return List.of();
    }
    return tags.stream().map(Tag::getName).toList();
  }
}
