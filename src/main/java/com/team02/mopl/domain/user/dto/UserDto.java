package com.team02.mopl.domain.user.dto;

import com.team02.mopl.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

public record UserDto(
    @Schema(description = "사용자 ID") UUID id,
    @Schema(description = "사용자 생성 시간") Instant createdAt,
    @Schema(description = "이메일") String email,
    @Schema(description = "사용자 이름") String name,
    @Schema(description = "프로필 이미지 URL") String profileImageUrl,
    @Schema(description = "사용자 역할") Role role,
    @Schema(description = "잠금 상태") boolean locked) {}
