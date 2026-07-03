package com.team02.mopl.domain.subscription.controller;

import com.team02.mopl.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Tag(name = "플레이리스트 관리")
public interface SubscriptionApi {

  @Operation(summary = "플레이리스트 구독")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(
        responseCode = "400",
        description = "잘못된 요청",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
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
  ResponseEntity<Void> subscribe(UUID playlistId, @Parameter(hidden = true) UUID requesterId);

  @Operation(summary = "플레이리스트 구독 취소")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "성공"),
    @ApiResponse(
        responseCode = "401",
        description = "인증 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "플레이리스트 또는 구독 정보를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<Void> unsubscribe(UUID playlistId, @Parameter(hidden = true) UUID requesterId);
}
