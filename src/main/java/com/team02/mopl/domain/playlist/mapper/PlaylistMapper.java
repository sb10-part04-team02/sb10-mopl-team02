package com.team02.mopl.domain.playlist.mapper;

import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

  // TODO: 사용자 조회 연동 시 UserService를 주입하여 owner의 name/profileImageUrl을 채운다.
  //             현재는 ownerId만 채운 스텁으로 생성한다.
  // TODO: 콘텐츠 조회 연동 시 playlistContents의 contentId로 ContentSummary를 채운다.
  //             현재는 빈 리스트 스텁으로 생성한다.
  public PlaylistDto toDto(Playlist playlist, boolean subscribedByMe) {
    return new PlaylistDto(
        playlist.getId(),
        new UserSummary(playlist.getOwnerId(), null, null),
        playlist.getTitle(),
        playlist.getDescription(),
        playlist.getUpdatedAt(),
        playlist.getSubscriberCount(),
        subscribedByMe,
        List.of());
  }
}
