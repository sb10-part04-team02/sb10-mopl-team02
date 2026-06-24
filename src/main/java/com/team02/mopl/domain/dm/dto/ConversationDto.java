package com.team02.mopl.domain.dm.dto;

import java.util.UUID;

public record ConversationDto(
    UUID id,
    UserSummary with,
    DirectMessageDto lastMessage,
    boolean hasUnread
) {

  //유저측에서 dto 구현 전까지 임시로 구현
  public record UserSummary(
      UUID id,
      String name,
      String profileImageUrl
  ) { }

}
