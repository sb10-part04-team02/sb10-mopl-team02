package com.team02.mopl.domain.content.ingestion.controller;

import com.team02.mopl.domain.content.ingestion.dto.IngestionTriggerRequest;
import com.team02.mopl.domain.content.ingestion.dto.IngestionTriggerResponse;
import com.team02.mopl.global.exception.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "콘텐츠 수집")
public interface ContentIngestionApi {

  // [어드민] 콘텐츠 수동 수집 - /api/contents/ingestion
  @Operation(
      summary = "[어드민] 콘텐츠 수동 수집",
      description =
          "선택한 소스의 콘텐츠 수집 배치를 즉시 실행합니다. 배치는 백그라운드로 실행되며 요청은 시작 직후 응답합니다. "
              + "소스를 생략하면 전체 소스를 수집합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "수집 시작됨"),
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
        responseCode = "409",
        description = "수집이 이미 실행 중",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "500",
        description = "서버 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  ResponseEntity<IngestionTriggerResponse> triggerIngestion(IngestionTriggerRequest request);
}
