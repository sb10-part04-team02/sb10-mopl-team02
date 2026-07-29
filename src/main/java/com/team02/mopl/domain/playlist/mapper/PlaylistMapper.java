package com.team02.mopl.domain.playlist.mapper;

import com.team02.mopl.domain.content.dto.ContentSummary;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.Tag;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.user.dto.UserSummary;
import com.team02.mopl.domain.user.entity.User;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

  // 이미 조회된 owner/contents를 조립
  public PlaylistDto toDto(
      Playlist playlist, UserSummary owner, List<ContentSummary> contents, boolean subscribedByMe) {
    return new PlaylistDto(
        playlist.getId(),
        owner,
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        playlist.getSubscriberCount(),
        subscribedByMe,
        contents);
  }

  public UserSummary toUserSummary(User user) {
    return new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl());
  }

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

  private List<String> toTagNames(List<Tag> tags) {
    if (tags == null) {
      return List.of();
    }
    return tags.stream().map(Tag::getName).toList();
  }
}
