package com.team02.mopl.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @Schema(description = "새 비밀번호") @NotBlank @Size(min = 8, message = "비밀번호는 8글자 이상입니다.")
        String password) {}
