package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ContentCreateRequest(
    @NotNull ContentType type,
    @NotBlank String title,
    @NotBlank String description,
    @NotNull List<String> tags) {}
