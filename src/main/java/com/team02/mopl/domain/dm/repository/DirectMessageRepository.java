package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.DirectMessage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DirectMessageRepository
    extends JpaRepository<DirectMessage, UUID>, DirectMessageRepositoryCustom {

  @EntityGraph(attributePaths = {"sender.user", "receiver.user", "conversation"})
  Optional<DirectMessage> findFirstByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);

  long countByConversationId(UUID conversationId);
}
