package com.team02.mopl.domain.content.controller;

import com.team02.mopl.domain.content.dto.ContentCreateRequest;
import com.team02.mopl.domain.content.dto.ContentDto;
import com.team02.mopl.domain.content.dto.ContentSearchRequest;
import com.team02.mopl.domain.content.dto.ContentUpdateRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "콘텐츠 관리")
public interface ContentApi {

  // [어드민] 콘텐츠 생성 - /api/contents
  @Operation(summary = "[어드민] 콘텐츠 생성", description = "콘텐츠를 생성합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "콘텐츠 생성 성공"),
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
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<ContentDto> createContent(ContentCreateRequest request, MultipartFile thumbnail);

  // 콘텐츠 단건 조회 - /api/contents/{contentId}
  @Operation(summary = "콘텐츠 단건 조회", description = "콘텐츠 ID로 단건 조회합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "콘텐츠 단건 조회 성공"),
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
  ResponseEntity<ContentDto> getContent(UUID contentId);

  // 콘텐츠 목록 조회 (커서 페이지네이션) - /api/contents
  @Operation(summary = "콘텐츠 목록 조회(커서 페이지네이션)", description = "커서 페이지네이션으로 콘텐츠 목록을 조회")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "콘텐츠 목록 조회 성공"),
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
  ResponseEntity<CursorResponse<ContentDto>> getContents(ContentSearchRequest request);

  // [어드민] 콘텐츠 수정 - /api/contents/{contentId}
  @Operation(summary = "[어드민] 콘텐츠 수정", description = "콘텐츠를 수정합니다. 썸네일은 선택. (ADMIN 전용)")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "수정 성공"),
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
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<ContentDto> updateContent(
      UUID contentId, ContentUpdateRequest request, MultipartFile thumbnail);

  // [어드민] 콘텐츠 삭제 - /api/contents/{contentId}
  @Operation(summary = "[어드민] 콘텐츠 삭제", description = "콘텐츠를 삭제합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "콘텐츠 삭제 성공"),
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
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<Void> deleteContent(UUID contentId);
}
