package com.team02.mopl.domain.playlist.controller;

import com.team02.mopl.domain.playlist.dto.PlaylistCreateRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistDto;
import com.team02.mopl.domain.playlist.dto.PlaylistSearchRequest;
import com.team02.mopl.domain.playlist.dto.PlaylistUpdateRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

@Tag(name = "플레이리스트 관리")
public interface PlaylistApi {

  @Operation(
      summary = "플레이리스트 단건 조회",
      description = "플레이리스트 ID로 소유자·포함 콘텐츠·구독자 수·요청자 구독 여부를 포함해 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "플레이리스트를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<PlaylistDto> getPlaylist(
      UUID playlistId, @Parameter(hidden = true) UUID requesterId);

  @Operation(
      summary = "플레이리스트 목록 조회",
      description = "플레이리스트 목록을 커서 기반 페이지네이션으로 조회합니다. keyword로 제목·설명을 부분일치 검색할 수 있습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<CursorResponse<PlaylistDto>> getPlaylists(
      PlaylistSearchRequest request, @Parameter(hidden = true) UUID requesterId);

  @Operation(summary = "플레이리스트 생성", description = "생성한 플레이리스트는 API 요청자 본인의 플레이리스트로 생성됩니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<PlaylistDto> createPlaylist(
      @Parameter(hidden = true) UUID ownerId, @RequestBody @Valid PlaylistCreateRequest request);

  @Operation(summary = "플레이리스트 수정", description = "플레이리스트 소유자만 수정할 수 있습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "권한 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "플레이리스트를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<PlaylistDto> updatePlaylist(
      UUID playlistId,
      @Parameter(hidden = true) UUID requesterId,
      @RequestBody @Valid PlaylistUpdateRequest request);

  @Operation(summary = "플레이리스트 삭제", description = "플레이리스트 소유자만 삭제할 수 있습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "권한 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "플레이리스트를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<Void> deletePlaylist(UUID playlistId, @Parameter(hidden = true) UUID requesterId);
}
