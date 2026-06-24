package com.team02.mopl.domain.dm.dto;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageDto(
    UUID id,
    UUID conversationId,
    Instant createdAt,
    UserSummary sender,
    UserSummary receiver,
    String content
) {
  //유저측에서 dto 구현 전까지 임시로 구현
  public record UserSummary(
      UUID id,
      String name,
      String profileImageUrl
  ) { }
}
