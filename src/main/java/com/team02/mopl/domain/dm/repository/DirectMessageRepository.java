package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.DirectMessage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository
    extends JpaRepository<DirectMessage, UUID>, DirectMessageRepositoryCustom {

  @EntityGraph(attributePaths = {"sender.user", "receiver.user", "conversation"})
  Optional<DirectMessage> findFirstByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);

  long countByConversationId(UUID conversationId);

  @EntityGraph(attributePaths = {"sender.user", "receiver.user", "conversation"})
  @Query(
      """
      SELECT dm FROM DirectMessage dm
      WHERE dm.conversation.id IN :conversationIds
      AND NOT EXISTS (
          SELECT 1 FROM DirectMessage dm2
          WHERE dm2.conversation = dm.conversation
          AND (dm2.createdAt > dm.createdAt
               OR (dm2.createdAt = dm.createdAt AND dm2.id > dm.id))
      )
      """)
  List<DirectMessage> findLatestByConversationIds(
      @Param("conversationIds") List<UUID> conversationIds);
}
