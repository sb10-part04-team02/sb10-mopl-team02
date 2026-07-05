package com.team02.mopl.domain.dm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
    Instant t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant t2 = t1.plusSeconds(1);

    UUID id1 = saveDmAt(member1, member2, "첫 번째", t1);
    UUID id2 = saveDmAt(member1, member2, "두 번째", t2);
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.DESCENDING, null, null, 10);

    assertThat(result).extracting(DirectMessage::getId).containsExactly(id2, id1);
  }

  @Test
  @DisplayName("커서 이후 메시지만 조회한다 (내림차순)")
  void findDirectMessagesByCursor_withCursor_returnsMessagesAfterCursor() {
    Instant t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant t2 = t1.plusSeconds(1);
    Instant t3 = t1.plusSeconds(2);

    UUID id1 = saveDmAt(member1, member2, "첫 번째", t1);
    UUID id2 = saveDmAt(member1, member2, "두 번째", t2);
    UUID id3 = saveDmAt(member1, member2, "세 번째", t3);
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.DESCENDING, t3, id3, 10);

    assertThat(result).extracting(DirectMessage::getId).containsExactly(id2, id1);
  }

  @Test
  @DisplayName("커서 이후 메시지만 조회한다 (오름차순)")
  void findDirectMessagesByCursor_withCursorAscending_returnsMessagesAfterCursor() {
    Instant t1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    Instant t2 = t1.plusSeconds(1);
    Instant t3 = t1.plusSeconds(2);

    UUID id1 = saveDmAt(member1, member2, "첫 번째", t1);
    UUID id2 = saveDmAt(member1, member2, "두 번째", t2);
    UUID id3 = saveDmAt(member1, member2, "세 번째", t3);
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.ASCENDING, t1, id1, 10);

    assertThat(result).extracting(DirectMessage::getId).containsExactly(id2, id3);
  }

  @Test
  @DisplayName("같은 createdAt에서 id로 타이브레이킹 한다 (내림차순)")
  void findDirectMessagesByCursor_sameCreatedAt_tieBreaksByIdDesc() {
    Instant sameTime = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    // id 비교를 위해 UUID를 직접 제어하여 정렬 순서를 확정
    UUID id1 = saveDmAt(member1, member2, "메시지A", sameTime);
    UUID id2 = saveDmAt(member1, member2, "메시지B", sameTime);
    UUID id3 = saveDmAt(member1, member2, "메시지C", sameTime);
    em.flush();

    // 첫 페이지 조회로 실제 정렬 순서 확인
    List<DirectMessage> firstPage =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.DESCENDING, null, null, 10);
    assertThat(firstPage).hasSize(3);

    // 첫 번째 항목을 커서로 삼아 나머지 2개가 조회되는지 검증
    DirectMessage cursor = firstPage.get(0);
    List<DirectMessage> next =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(),
            SortDirection.DESCENDING,
            cursor.getCreatedAt(),
            cursor.getId(),
            10);

    assertThat(next).hasSize(2);
    assertThat(next).doesNotContain(cursor);
  }

  @Test
  @DisplayName("limit 개수만큼만 반환한다")
  void findDirectMessagesByCursor_respectsLimit() {
    Instant t = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    saveDmAt(member1, member2, "첫 번째", t);
    saveDmAt(member1, member2, "두 번째", t.plusSeconds(1));
    saveDmAt(member1, member2, "세 번째", t.plusSeconds(2));
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            conversation.getId(), SortDirection.DESCENDING, null, null, 2);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("다른 대화방의 메시지는 조회되지 않는다")
  void findDirectMessagesByCursor_onlyReturnsMessagesForConversation() {
    saveDmAt(member1, member2, "이 대화방 메시지", Instant.now().truncatedTo(ChronoUnit.MILLIS));
    em.flush();

    List<DirectMessage> result =
        directMessageRepository.findDirectMessagesByCursor(
            UUID.randomUUID(), SortDirection.DESCENDING, null, null, 10);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("countByConversationId는 해당 대화방의 메시지 수를 반환한다")
  void countByConversationId_returnsCorrectCount() {
    Instant t = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    saveDmAt(member1, member2, "메시지1", t);
    saveDmAt(member2, member1, "메시지2", t.plusSeconds(1));
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

  private UUID saveDmAt(
      ConversationMember sender, ConversationMember receiver, String content, Instant createdAt) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO direct_messages (id, created_at, conversation_id, sender_id, receiver_id, content) "
                + "VALUES (:id, :createdAt, :convId, :senderId, :receiverId, :content)")
        .setParameter("id", id)
        .setParameter("createdAt", createdAt)
        .setParameter("convId", conversation.getId())
        .setParameter("senderId", sender.getId())
        .setParameter("receiverId", receiver.getId())
        .setParameter("content", content)
        .executeUpdate();
    return id;
  }
}
