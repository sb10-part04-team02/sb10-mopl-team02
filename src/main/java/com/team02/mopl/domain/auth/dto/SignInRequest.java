package com.team02.mopl.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignInRequest(
    @Schema(description = "이메일")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @NotBlank(message = "이메일은 필수입니다.")
        String email,
    @Schema(description = "비밀번호") @Size(min = 8, message = "비밀번호는 8글자 이상입니다.") @NotBlank
        String password) {}
