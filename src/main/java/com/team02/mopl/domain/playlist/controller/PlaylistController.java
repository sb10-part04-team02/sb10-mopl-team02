package com.team02.mopl.domain.playlist.controller;

import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.dto.PlaylistSearchRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistUpdateRequest;
import com.team02.mopl.domain.playlist.service.PlaylistService;
import com.team02.mopl.global.dto.CursorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
public class PlaylistController implements PlaylistApi {

  private final PlaylistService playlistService;

  @GetMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> getPlaylist(
      @PathVariable UUID playlistId, @AuthenticationPrincipal UUID requesterId) {
    return ResponseEntity.ok(playlistService.get(playlistId, requesterId));
  }

  @GetMapping
  public ResponseEntity<CursorResponse<PlaylistDto>> getPlaylists(
      @ParameterObject @ModelAttribute @Valid PlaylistSearchRequest request,
      @AuthenticationPrincipal UUID requesterId) {
    return ResponseEntity.ok(playlistService.getPlaylists(request, requesterId));
  }

  // TODO: JWT 인증 연결 후 이 엔드포인트를 인증 필수로 보호한다.
  //       현재 SecurityConfig가 anyRequest().permitAll() 상태라 ownerId(principal)를 신뢰할 수 없음.
  @PostMapping
  public ResponseEntity<PlaylistDto> createPlaylist(
      @AuthenticationPrincipal UUID ownerId, @RequestBody @Valid PlaylistCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(playlistService.create(ownerId, request));
  }

  @PatchMapping("/{playlistId}")
  public ResponseEntity<PlaylistDto> updatePlaylist(
      @PathVariable UUID playlistId,
      @AuthenticationPrincipal UUID requesterId,
      @RequestBody @Valid PlaylistUpdateRequest request) {
    return ResponseEntity.ok(playlistService.update(playlistId, requesterId, request));
  }

  @DeleteMapping("/{playlistId}")
  public ResponseEntity<Void> deletePlaylist(
      @PathVariable UUID playlistId, @AuthenticationPrincipal UUID requesterId) {
    playlistService.delete(playlistId, requesterId);
    return ResponseEntity.noContent().build();
  }
}
