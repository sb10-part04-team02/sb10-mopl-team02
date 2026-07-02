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

  @Query(
      value =
          """
          SELECT DISTINCT ON (conversation_id) id
          FROM direct_messages
          WHERE conversation_id IN :conversationIds
          ORDER BY conversation_id, created_at DESC, id DESC
          """,
      nativeQuery = true)
  List<UUID> findLatestMessageIdsByConversationIds(
      @Param("conversationIds") List<UUID> conversationIds);

  @EntityGraph(attributePaths = {"sender.user", "receiver.user", "conversation"})
  List<DirectMessage> findByIdIn(List<UUID> ids);
}
