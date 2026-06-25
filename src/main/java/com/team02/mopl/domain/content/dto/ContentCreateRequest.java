package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContentCreateRequest(
    @NotNull ContentType type,
    @NotBlank @Size(max = 100) String title,
    @NotBlank @Size(max = 255) String description,
    @NotNull List<@NotBlank @Size(max = 20) String> tags) {}
