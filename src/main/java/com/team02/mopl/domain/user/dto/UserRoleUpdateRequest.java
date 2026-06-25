package com.team02.mopl.domain.user.dto;

import com.team02.mopl.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserRoleUpdateRequest(@Schema(description = "변경할 사용자 역할") @NotNull Role role) {}
