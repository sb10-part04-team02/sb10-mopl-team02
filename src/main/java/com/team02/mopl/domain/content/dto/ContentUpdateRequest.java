package com.team02.mopl.domain.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContentUpdateRequest(
    @Schema(description = "콘텐츠 제목") @Size(max = 100) String title,
    @Schema(description = "콘텐츠 설명") @Size(max = 255) String description,
    @Schema(description = "콘텐츠 태그 목록") List<@NotBlank @Size(max = 20) String> tags) {}
