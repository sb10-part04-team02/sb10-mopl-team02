package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.DirectMessage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

  @Query(
      """
      SELECT dm FROM DirectMessage dm
      JOIN FETCH dm.sender s
      JOIN FETCH s.user su
      JOIN FETCH dm.receiver r
      JOIN FETCH r.user ru
      WHERE dm.conversation.id = :conversationId
      ORDER BY dm.createdAt DESC
      LIMIT 1
      """)
  Optional<DirectMessage> findLastByConversationId(@Param("conversationId") UUID conversationId);
}
