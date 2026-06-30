package com.team02.mopl.domain.playlist.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.dto.PlaylistUpdateRequest;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.exception.PlaylistForbiddenException;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.mapper.PlaylistMapper;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import com.team02.mopl.domain.user.dto.UserSummary;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

// TODO: JWT 인증 연결 후 PlaylistController @WebMvcTest 추가
//       (201 응답 / @Valid 검증 실패 400 / 인증된 요청자 UUID 바인딩 검증).
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

  @Nested
  class Update {
    private final UUID playlistId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    @DisplayName("소유자가 요청하면 제목·설명을 수정하고 PlaylistDto를 반환한다")
    void success_whenRequesterIsOwner() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      PlaylistDto expect =
          new PlaylistDto(
              playlistId,
              new UserSummary(ownerId, null, null),
              "새 제목",
              "새 설명",
              Instant.parse("2026-06-29T00:00:00Z"),
              0L,
              false,
              List.of());
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(expect);

      // when
      PlaylistDto actual = playlistService.update(playlistId, ownerId, request);

      // then
      assertThat(actual).isEqualTo(expect);
      assertThat(playlist.getTitle()).isEqualTo("새 제목");
      assertThat(playlist.getDescription()).isEqualTo("새 설명");
      then(playlistMapper).should().toDto(playlist, false);
    }

    @Test
    @DisplayName("title만 전달하면 description은 기존 값을 유지한다")
    void success_whenPartialUpdate() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", null);
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(null);

      // when
      playlistService.update(playlistId, ownerId, request);

      // then
      assertThat(playlist.getTitle()).isEqualTo("새 제목");
      assertThat(playlist.getDescription()).isEqualTo("기존 설명");
    }

    @Test
    @DisplayName("빈 문자열이 전달되면 기존 값을 덮어쓰지 않는다")
    void success_whenBlankIsIgnored() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("", "   ");
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));
      given(playlistMapper.toDto(playlist, false)).willReturn(null);

      // when
      playlistService.update(playlistId, ownerId, request);

      // then
      assertThat(playlist.getTitle()).isEqualTo("기존 제목");
      assertThat(playlist.getDescription()).isEqualTo("기존 설명");
    }

    @Test
    @DisplayName("플레이리스트가 없으면 PLAYLIST_NOT_FOUND 예외를 던진다")
    void fail_whenPlaylistNotFound() {
      // given
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      given(playlistRepository.findById(playlistId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> playlistService.update(playlistId, ownerId, request))
          .isInstanceOf(PlaylistNotFoundException.class);
      then(playlistMapper).should(never()).toDto(any(Playlist.class), eq(false));
    }

    @Test
    @DisplayName("요청자가 소유자가 아니면 FORBIDDEN 예외를 던진다")
    void fail_whenRequesterIsNotOwner() {
      // given
      Playlist playlist = new Playlist(ownerId, "기존 제목", "기존 설명");
      UUID otherUserId = UUID.randomUUID();
      PlaylistUpdateRequest request = new PlaylistUpdateRequest("새 제목", "새 설명");
      given(playlistRepository.findById(playlistId)).willReturn(Optional.of(playlist));

      // when & then
      assertThatThrownBy(() -> playlistService.update(playlistId, otherUserId, request))
          .isInstanceOf(PlaylistForbiddenException.class);
      assertThat(playlist.getTitle()).isEqualTo("기존 제목");
      then(playlistMapper).should(never()).toDto(any(Playlist.class), eq(false));
    }
  }
}
