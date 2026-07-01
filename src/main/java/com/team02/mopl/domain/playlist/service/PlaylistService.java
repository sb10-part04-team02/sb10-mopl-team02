package com.team02.mopl.domain.playlist.service;

import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.dto.PlaylistUpdateRequest;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.exception.PlaylistForbiddenException;
import com.team02.mopl.domain.playlist.exception.PlaylistNotFoundException;
import com.team02.mopl.domain.playlist.mapper.PlaylistMapper;
import com.team02.mopl.domain.playlist.repository.PlaylistRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaylistService {

  private final PlaylistRepository playlistRepository;
  private final PlaylistMapper playlistMapper;

  @Transactional
  public PlaylistDto create(UUID ownerId, PlaylistCreateRequest request) {
    log.debug("플레이리스트 생성 시작: ownerId={}", ownerId);

    Playlist playlist = new Playlist(ownerId, request.title(), request.description());
    Playlist saved = playlistRepository.save(playlist);

    // 방금 생성한 본인 플레이리스트이므로 subscribedByMe는 false
    PlaylistDto playlistDto = playlistMapper.toDto(saved, false);

    log.info("플레이리스트 생성 성공: playlistId={}, ownerId={}", playlistDto.id(), ownerId);
    return playlistDto;
  }

  @Transactional
  public PlaylistDto update(UUID playlistId, UUID requesterId, PlaylistUpdateRequest request) {
    log.debug("플레이리스트 수정 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    Playlist playlist =
        playlistRepository
            .findByIdAndDeletedAtIsNull(playlistId)
            .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwnerId().equals(requesterId)) {
      throw new PlaylistForbiddenException();
    }

    playlist.update(request.title(), request.description());
    playlistRepository.flush();
    // 소유자 본인의 플레이리스트이므로 subscribedByMe는 false
    PlaylistDto playlistDto = playlistMapper.toDto(playlist, false);

    log.info("플레이리스트 수정 성공: playlistId={}, requesterId={}", playlistId, requesterId);
    return playlistDto;
  }

  @Transactional
  public void delete(UUID playlistId, UUID requesterId) {
    log.debug("플레이리스트 삭제 시작: playlistId={}, requesterId={}", playlistId, requesterId);

    Playlist playlist =
        playlistRepository
            .findByIdAndDeletedAtIsNull(playlistId)
            .orElseThrow(PlaylistNotFoundException::new);

    if (!playlist.getOwnerId().equals(requesterId)) {
      throw new PlaylistForbiddenException();
    }

    playlist.delete();
    playlistRepository.flush();

    log.info("플레이리스트 삭제 성공: playlistId={}, requesterId={}", playlistId, requesterId);
  }
}
