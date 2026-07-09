package com.team02.mopl.domain.watching.mapper;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.watching.dto.WatchingSessionDto;
import com.team02.mopl.domain.watching.entity.WatchingSession;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WatchingSessionMapper {

  public ContentSummary toContentSummary(Content content, List<Tag> tags) {
    return new ContentSummary(
        content.getId(),
        content.getContentType(),
        content.getTitle(),
        content.getDescription(),
        content.getThumbnailUrl(),
        toTagNames(tags),
        content.getAverageRating(),
        content.getReviewCount());
  }

  public WatchingSessionDto toDto(WatchingSession session, ContentSummary contentSummary) {
    User watcher = session.getUser(); // 목록 조회 시 fetch join으로 이미 로딩됨
    return new WatchingSessionDto(
        session.getId(),
        session.getCreatedAt(),
        new UserSummary(watcher.getId(), watcher.getName(), watcher.getProfileImageUrl()),
        contentSummary);
  }

  private List<String> toTagNames(List<Tag> tags) {
    if (tags == null) {
      return List.of();
    }
    return tags.stream().map(Tag::getName).toList();
  }
}
