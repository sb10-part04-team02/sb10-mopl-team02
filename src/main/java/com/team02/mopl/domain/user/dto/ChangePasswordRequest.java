package com.team02.mopl.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
    @Schema(description = "새 비밀번호")
        @Size(min = 8, max = 20, message = "비밀번호는 8글자 이상, 20자 이하입니다.")
        @NotBlank
        String password) {}
