package com.team02.mopl.domain.notification.controller;

import com.team02.mopl.domain.notification.dto.NotificationDto;
import com.team02.mopl.domain.notification.dto.NotificationSearchRequest;
import com.team02.mopl.global.dto.CursorResponse;
import com.team02.mopl.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;

public interface NotificationApi {

  @Operation(summary = "알림 목록 조회", description = "로그인 사용자의 알림 목록을 커서 기반으로 조회합니다.")
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
  ResponseEntity<CursorResponse<NotificationDto>> getNotifications(
      @Parameter(hidden = true) UUID receiverId,
      @ParameterObject @Valid NotificationSearchRequest request);

  @Operation(summary = "알림 읽음 처리", description = "로그인 사용자의 알림을 읽음 처리합니다.")
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
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<Void> markAsRead(@Parameter(hidden = true) UUID receiverId, UUID notificationId);
}
