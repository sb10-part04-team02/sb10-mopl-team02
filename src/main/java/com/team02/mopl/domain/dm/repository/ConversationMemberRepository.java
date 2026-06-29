package com.team02.mopl.domain.dm.repository;

import com.team02.mopl.domain.dm.entity.ConversationMember;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, UUID> {

  @Query(
      """
      SELECT COUNT(cm1) > 0 FROM ConversationMember cm1
      WHERE cm1.user.id = :userAId
      AND cm1.conversation IN (
        SELECT cm2.conversation FROM ConversationMember cm2
        WHERE cm2.user.id = :userBId
      )
      """)
  boolean existsConversationBetween(@Param("userAId") UUID userAId, @Param("userBId") UUID userBId);

  // 대화방에서 요청자가 아닌 상대방 멤버 조회
  @Query(
      """
      SELECT cm FROM ConversationMember cm
      JOIN FETCH cm.user
      WHERE cm.conversation.id = :conversationId
      AND cm.user.id != :requesterId
      """)
  java.util.Optional<ConversationMember> findWithUserMember(
      @Param("conversationId") UUID conversationId, @Param("requesterId") UUID requesterId);

  // 두 유저가 공유하는 대화방에서 상대방 멤버 조회
  @Query(
      """
      SELECT cm FROM ConversationMember cm
      JOIN FETCH cm.user
      WHERE cm.user.id = :withUserId
      AND cm.conversation IN (
        SELECT cm2.conversation FROM ConversationMember cm2
        WHERE cm2.user.id = :requesterId
      )
      """)
  java.util.Optional<ConversationMember> findWithUserMemberByUserIds(
      @Param("requesterId") UUID requesterId, @Param("withUserId") UUID withUserId);
}
