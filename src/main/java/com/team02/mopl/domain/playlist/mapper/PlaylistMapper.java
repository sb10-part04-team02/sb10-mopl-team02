package com.team02.mopl.domain.playlist.mapper;

import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PlaylistMapper {

  // TODO: 사용자/콘텐츠 조회는 서비스 계층에서 수행하고, 이미 조회된 UserSummary/ContentSummary를
  //             매퍼 인자로 넘기도록 시그니처를 확장한다(매퍼는 조립만 담당).
  //             현재는 owner를 ownerId 스텁, contents를 빈 리스트 스텁으로 생성한다.
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
