package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContentCreateRequest(
    @Schema(description = "콘텐츠 타입") @NotNull ContentType type,
    @Schema(description = "콘텐츠 제목") @NotBlank @Size(max = 100) String title,
    @Schema(description = "콘텐츠 설명") @NotBlank @Size(max = 255) String description,
    @Schema(description = "콘텐츠 태그 목록") @NotNull List<@NotBlank @Size(max = 20) String> tags) {}
