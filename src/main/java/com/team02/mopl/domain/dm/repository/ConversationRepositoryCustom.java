package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.global.enums.SortDirection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ConversationRepositoryCustom {

  List<Conversation> findConversationsByCursor(
      UUID userId, SortDirection direction, Instant cursor, UUID idAfter, int limit);
}
