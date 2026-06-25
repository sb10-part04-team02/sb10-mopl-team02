package com.team02.mopl.domain.review.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReviewCreateRequest(
    @NotNull UUID contentId,
    @NotBlank @Size(max = 500) String text,
    @NotNull @DecimalMin("0.0") @DecimalMax("5.0") Double rating) {}
