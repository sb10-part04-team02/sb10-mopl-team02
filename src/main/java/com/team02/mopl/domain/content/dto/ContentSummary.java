package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import java.util.List;
import java.util.UUID;

public record ContentSummary(
    UUID id,
    ContentType type,
    String title,
    String description,
    String thumbnailUrl,
    List<String> tags,
    double averageRating,
    int reviewCount) {}
