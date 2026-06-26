package com.team02.mopl.domain.follow.controller;

import com.team02.mopl.domain.follow.dto.FollowDto;
import com.team02.mopl.domain.follow.dto.FollowRequest;
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
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "팔로우 관리")
public interface FollowApi {

  @Operation(summary = "팔로우", description = "로그인 사용자가 특정 사용자를 팔로우합니다.")
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
  ResponseEntity<FollowDto> createFollow(
      @Parameter(hidden = true) UUID followerId, @RequestBody @Valid FollowRequest request);

  @Operation(summary = "팔로우 여부 조회", description = "로그인 사용자가 특정 사용자를 팔로우 중인지 조회합니다.")
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
        responseCode = "404",
        description = "해당 리소스 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<FollowDto> getFollowedByMe(
      @Parameter(hidden = true) UUID followerId, @RequestParam UUID followeeId);

  @Operation(summary = "팔로워 수 조회", description = "특정 사용자를 팔로우하는 사용자 수를 조회합니다.")
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
  ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId);

  @Operation(summary = "팔로우 취소", description = "로그인 사용자가 본인이 생성한 팔로우 관계를 취소합니다.")
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
        responseCode = "403",
        description = "권한 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<Void> cancelFollow(@Parameter(hidden = true) UUID requesterId, UUID followId);
}
