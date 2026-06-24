package com.team02.mopl.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
    @Schema(description = "사용자 이름") @Size(min = 2, message = "이름은 2글자 이상입니다.") String name) {}
