package com.team02.mopl.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

public record UserSummary(
    @Schema(description = "사용자 ID") UUID userId,
    @Schema(description = "사용자 이름") String name,
    @Schema(description = "프로필 이미지 URL") String profileImageUrl) {}
