package com.team02.mopl.domain.playlist.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlaylistMapperTest {

  private final PlaylistMapper playlistMapper = new PlaylistMapper();

  @Test
  @DisplayName("Playlist 엔티티를 PlaylistDto로 변환하면 기본 필드가 매핑되고 owner는 ownerId 스텁, contents는 빈 리스트가 된다")
  void toDto_mapsFieldsAndStubsOwnerAndContents() {
    UUID ownerId = UUID.randomUUID();
    Playlist playlist = new Playlist(ownerId, "내 플리", "설명");

    PlaylistDto dto = playlistMapper.toDto(playlist, true);

    assertThat(dto.title()).isEqualTo("내 플리");
    assertThat(dto.description()).isEqualTo("설명");
    assertThat(dto.subscriberCount()).isEqualTo(0L);
    assertThat(dto.subscribedByMe()).isTrue();
    assertThat(dto.owner().userId()).isEqualTo(ownerId);
    assertThat(dto.owner().name()).isNull();
    assertThat(dto.owner().profileImageUrl()).isNull();
    assertThat(dto.contents()).isEmpty();
  }
}
