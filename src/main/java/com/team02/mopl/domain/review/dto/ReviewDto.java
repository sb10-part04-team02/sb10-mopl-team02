package com.team02.mopl.domain.review.dto;

import com.team02.mopl.domain.user.dto.UserSummary;
import java.util.UUID;

public record ReviewDto(UUID id, UUID contentId, UserSummary author, String text, double rating) {}
