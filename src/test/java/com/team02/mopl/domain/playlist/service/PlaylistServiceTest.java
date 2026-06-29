package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.mapper.PlaylistMapper;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

  @Mock PlaylistRepository playlistRepository;

  @Mock PlaylistMapper playlistMapper;

  @InjectMocks PlaylistService playlistService;

  @Captor ArgumentCaptor<Playlist> playlistCaptor;

  @Nested
  class Create {
    private final UUID ownerId = UUID.randomUUID();
    private final String title = "내 플리";
    private final String description = "설명";

    @Test
    @DisplayName("정상 요청이면 소유자를 요청자로 지정해 저장하고 PlaylistDto를 반환한다")
    void success_whenRequestIsValid() {
      // given
      PlaylistCreateRequest request = new PlaylistCreateRequest(title, description);
      Playlist saved = new Playlist(ownerId, title, description);
      PlaylistDto expect =
          new PlaylistDto(
              UUID.randomUUID(),
              new UserSummary(ownerId, null, null),
              title,
              description,
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());
      given(playlistRepository.save(any(Playlist.class))).willReturn(saved);
      given(playlistMapper.toDto(any(Playlist.class), eq(false))).willReturn(expect);

      // when
      PlaylistDto actual = playlistService.create(ownerId, request);

      // then
      assertThat(actual).isEqualTo(expect);
      then(playlistRepository).should().save(playlistCaptor.capture());
      then(playlistMapper).should().toDto(any(Playlist.class), eq(false));

      Playlist captured = playlistCaptor.getValue();
      assertThat(captured.getOwnerId()).isEqualTo(ownerId);
      assertThat(captured.getTitle()).isEqualTo(title);
      assertThat(captured.getDescription()).isEqualTo(description);
    }
  }
}
