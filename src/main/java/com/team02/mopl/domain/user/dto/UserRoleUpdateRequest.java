package com.team02.mopl.domain.user.dto;

import com.team02.mopl.domain.user.entity.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserRoleUpdateRequest(@Schema(description = "변경할 잠금 상태") Role role) {}
