package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.Conversation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationRepository
    extends JpaRepository<Conversation, UUID>, ConversationRepositoryCustom {

  @Query(
      """
      SELECT COUNT(c) FROM Conversation c
      JOIN ConversationMember cm ON cm.conversation = c
      WHERE cm.user.id = :userId
      AND c.deletedAt IS NULL
      """)
  long countByMemberUserId(@Param("userId") UUID userId);
}
