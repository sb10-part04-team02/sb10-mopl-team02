package com.team02.mopl.domain.content.ingestion.dto;

import com.team02.mopl.domain.content.enums.ContentSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

public record IngestionTriggerResponse(
    @Schema(description = "안내 메시지", example = "콘텐츠 수집을 시작했습니다.") String message,
    @Schema(description = "수집을 시작한 소스 목록") Set<ContentSource> sources) {}
