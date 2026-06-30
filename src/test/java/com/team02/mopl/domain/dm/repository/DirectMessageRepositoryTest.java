package com.team02.mopl.domain.dm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DirectMessageRepositoryTest extends RepositoryTestSupport {

  @Autowired private DirectMessageRepository directMessageRepository;
  @Autowired private ConversationRepository conversationRepository;
  @Autowired private ConversationMemberRepository conversationMemberRepository;

  @Autowired private EntityManager em;

  private Conversation conversation;
  private ConversationMember member1;
  private ConversationMember member2;

  @BeforeEach
  void setUp() {
    UUID userId1 = insertUser("user1@test.com");
    UUID userId2 = insertUser("user2@test.com");

    conversation = conversationRepository.save(new Conversation());
    em.flush();

    member1 = saveMember(conversation, userId1);
    member2 = saveMember(conversation, userId2);
    em.flush();
  }

  @Test
  @DisplayName("커서 없이 조회하면 createdAt 내림차순 첫 페이지를 반환한다")
  void findDirectMessagesByCursor_firstPage_returnsDescOrder() {
    DirectMessage dm1 = saveDm(member1, member2, "첫 번째");
    DirectMessage dm2 = saveDm(member1, member2, "두 번째");
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.DESCENDING, null, null, 10);

    assertThat(result).extracting(DirectMessage::getId).containsExactly(dm2.getId(), dm1.getId());
  }

  @Test
  @DisplayName("커서 이후 메시지만 조회한다 (내림차순)")
  void findDirectMessagesByCursor_withCursor_returnsMessagesAfterCursor() {
    DirectMessage dm1 = saveDm(member1, member2, "첫 번째");
    DirectMessage dm2 = saveDm(member1, member2, "두 번째");
    DirectMessage dm3 = saveDm(member1, member2, "세 번째");
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(),
            SortDirection.DESCENDING,
            dm3.getCreatedAt().toString(),
            dm3.getId(),
            10);

    assertThat(result).extracting(DirectMessage::getId).containsExactly(dm2.getId(), dm1.getId());
  }

  @Test
  @DisplayName("커서 이후 메시지만 조회한다 (오름차순)")
  void findDirectMessagesByCursor_withCursorAscending_returnsMessagesAfterCursor() {
    DirectMessage dm1 = saveDm(member1, member2, "첫 번째");
    DirectMessage dm2 = saveDm(member1, member2, "두 번째");
    DirectMessage dm3 = saveDm(member1, member2, "세 번째");
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(),
            SortDirection.ASCENDING,
            dm1.getCreatedAt().toString(),
            dm1.getId(),
            10);

    assertThat(result).extracting(DirectMessage::getId).containsExactly(dm2.getId(), dm3.getId());
  }

  @Test
  @DisplayName("limit 개수만큼만 반환한다")
  void findDirectMessagesByCursor_respectsLimit() {
    saveDm(member1, member2, "첫 번째");
    saveDm(member1, member2, "두 번째");
    saveDm(member1, member2, "세 번째");
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.DESCENDING, null, null, 2);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("다른 대화방의 메시지는 조회되지 않는다")
  void findDirectMessagesByCursor_onlyReturnsMessagesForConversation() {
    saveDm(member1, member2, "이 대화방 메시지");
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            UUID.randomUUID(), SortDirection.DESCENDING, null, null, 10);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("cursor와 idAfter 중 하나만 전달되면 INVALID_REQUEST 예외를 던진다")
  void findDirectMessagesByCursor_partialCursor_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                directMessageRepository.findDirectMessagesByCursor(
                    conversation.getId(), SortDirection.DESCENDING, null, UUID.randomUUID(), 10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("cursor가 ISO-8601 형식이 아니면 INVALID_REQUEST 예외를 던진다")
  void findDirectMessagesByCursor_invalidCursorFormat_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                directMessageRepository.findDirectMessagesByCursor(
                    conversation.getId(),
                    SortDirection.DESCENDING,
                    "not-a-timestamp",
                    UUID.randomUUID(),
                    10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("countByConversationId는 해당 대화방의 메시지 수를 반환한다")
  void countByConversationId_returnsCorrectCount() {
    saveDm(member1, member2, "메시지1");
    saveDm(member2, member1, "메시지2");
    em.flush();

    long count = directMessageRepository.countByConversationId(conversation.getId());

    assertThat(count).isEqualTo(2L);
  }

  // ──────────────────────────────────────────────
  // helpers
  // ──────────────────────────────────────────────

  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", "테스트유저")
        .setParameter("email", email)
        .executeUpdate();
    return id;
  }

  private ConversationMember saveMember(Conversation conv, UUID userId) {
    em.flush();
    em.createNativeQuery(
            "INSERT INTO conversation_members (id, updated_at, conversation_id, member_id, last_read_at) "
                + "VALUES (:id, now(), :convId, :memberId, now())")
        .setParameter("id", UUID.randomUUID())
        .setParameter("convId", conv.getId())
        .setParameter("memberId", userId)
        .executeUpdate();
    em.flush();
    return conversationMemberRepository
        .findByConversationIdAndUserId(conv.getId(), userId)
        .orElseThrow();
  }

  private DirectMessage saveDm(
      ConversationMember sender, ConversationMember receiver, String content) {
    return directMessageRepository.save(
        DirectMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .receiver(receiver)
            .content(content)
            .build());
  }
}
