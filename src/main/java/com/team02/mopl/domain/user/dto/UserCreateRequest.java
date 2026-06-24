package com.team02.mopl.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UserCreateRequest(
    @Schema(description = "이름") @NotBlank @Min(value = 2, message = "이름은 2글자 이상입니다.") String name,
    @Schema(description = "이메일", example = "example@gmail.com")
        @NotBlank
        @Pattern(
            regexp = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}$", // 실무용 이메일 정규패턴
            message = "올바른 이메일 형식이 아닙니다.")
        String email,
    @Schema(description = "비밀번호") @NotBlank @Min(value = 8, message = "비밀번호는 8글자 이상입니다.")
        String password) {}
