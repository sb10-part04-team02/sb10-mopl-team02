package com.team02.mopl.domain.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContentUpdateRequest(
    @Size(max = 100) String title,
    @Size(max = 255) String description,
    List<@NotBlank @Size(max = 20) String> tags) {}
