package com.team02.mopl.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UserLockUpdateRequest(@Schema(description = "변경할 잠금 상태") @NotNull Boolean locked) {}
