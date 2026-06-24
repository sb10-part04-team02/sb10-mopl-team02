package com.team02.mopl.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserCreateRequest(
    @Schema(description = "이름") @NotBlank @Size(min = 2, message = "이름은 2글자 이상입니다.") String name,
    @Schema(description = "이메일")
        @NotBlank
        @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", // 실무용 이메일 정규패턴
            message = "올바른 이메일 형식이 아닙니다.")
        String email,
    @Schema(description = "비밀번호") @NotBlank @Size(min = 8, message = "비밀번호는 8글자 이상입니다.")
        String password) {}
