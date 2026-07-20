package com.team02.mopl.domain.content.ingestion.dto;

import com.team02.mopl.domain.content.enums.ContentSource;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record IngestionTriggerRequest(
    @Schema(description = "수집할 소스 목록. 생략하거나 비우면 전체 소스를 수집합니다.", example = "[\"TMDB\"]")
        Set<@NotNull ContentSource> sources) {}
