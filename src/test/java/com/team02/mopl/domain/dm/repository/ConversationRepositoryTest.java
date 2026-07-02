package com.team02.mopl.domain.dm.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConversationRepositoryTest extends RepositoryTestSupport {

  @Autowired private ConversationRepository conversationRepository;
  @Autowired private EntityManager em;

  private UUID userId;
  private UUID otherUserId;

  @BeforeEach
  void setUp() {
    userId = insertUser("user@test.com");
    otherUserId = insertUser("other@test.com");
  }

  @Test
  @DisplayName("첫 페이지 조회 시 해당 사용자의 대화 목록을 createdAt 내림차순으로 반환한다")
  void findConversationsByCursor_firstPage_returnsDescOrder() {
    Conversation conv1 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    Conversation conv2 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T03:00:00Z"));

    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, SortDirection.DESCENDING, null, null, 10);

    assertThat(result)
        .extracting(Conversation::getId)
        .containsExactly(conv2.getId(), conv1.getId());
  }

  @Test
  @DisplayName("오름차순 요청 시 오래된 대화부터 반환한다")
  void findConversationsByCursor_ascending_returnsOldestFirst() {
    Conversation conv1 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    Conversation conv2 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T03:00:00Z"));

    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, SortDirection.ASCENDING, null, null, 10);

    assertThat(result)
        .extracting(Conversation::getId)
        .containsExactly(conv1.getId(), conv2.getId());
  }

  @Test
  @DisplayName("커서가 있으면 커서 이후의 다음 페이지를 반환한다")
  void findConversationsByCursor_withCursor_returnsNextPage() {
    Conversation conv1 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    Conversation conv2 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T02:00:00Z"));
    Conversation conv3 =
        saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T03:00:00Z"));

    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, SortDirection.DESCENDING, conv3.getCreatedAt().toString(), conv3.getId(), 10);

    assertThat(result)
        .extracting(Conversation::getId)
        .containsExactly(conv2.getId(), conv1.getId());
  }

  @Test
  @DisplayName("자신이 참여하지 않은 대화는 반환하지 않는다")
  void findConversationsByCursor_excludesConversationsNotJoined() {
    UUID thirdUserId = insertUser("third@test.com");
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    saveConversationWithMembers(otherUserId, thirdUserId, Instant.parse("2026-06-29T02:00:00Z"));

    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, SortDirection.DESCENDING, null, null, 10);

    assertThat(result).hasSize(1);
  }

  @Test
  @DisplayName("limit만큼만 반환한다")
  void findConversationsByCursor_respectsLimit() {
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T02:00:00Z"));
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T03:00:00Z"));

    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, SortDirection.DESCENDING, null, null, 2);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("cursor만 있고 idAfter가 없으면 INVALID_REQUEST 예외가 발생한다")
  void findConversationsByCursor_cursorWithoutIdAfter_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                conversationRepository.findConversationsByCursor(
                    userId, SortDirection.DESCENDING, "2026-06-29T00:00:00Z", null, 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("잘못된 cursor 형식이면 INVALID_REQUEST 예외가 발생한다")
  void findConversationsByCursor_invalidCursor_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                conversationRepository.findConversationsByCursor(
                    userId, SortDirection.DESCENDING, "not-a-date", UUID.randomUUID(), 10))
        .isInstanceOfSatisfying(
            BusinessException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
  }

  @Test
  @DisplayName("사용자의 활성 대화 수를 반환한다")
  void countByMemberUserId_returnsActiveConversationCount() {
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T02:00:00Z"));

    UUID thirdUserId = insertUser("third@test.com");
    saveConversationWithMembers(otherUserId, thirdUserId, Instant.parse("2026-06-29T03:00:00Z"));

    long count = conversationRepository.countByMemberUserId(userId);

    assertThat(count).isEqualTo(2L);
  }

  private UUID insertUser(String email) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", "테스트유저")
        .setParameter("email", email)
        .executeUpdate();
    em.flush();
    return id;
  }

  private Conversation saveConversationWithMembers(UUID userAId, UUID userBId, Instant createdAt) {
    UUID convId = UUID.randomUUID();
    em.createNativeQuery("INSERT INTO conversations (id, created_at) VALUES (:id, :createdAt)")
        .setParameter("id", convId)
        .setParameter("createdAt", createdAt)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO conversation_members (id, updated_at, conversation_id, member_id, last_read_at) "
                + "VALUES (:id, now(), :convId, :memberId, now())")
        .setParameter("id", UUID.randomUUID())
        .setParameter("convId", convId)
        .setParameter("memberId", userAId)
        .executeUpdate();

    em.createNativeQuery(
            "INSERT INTO conversation_members (id, updated_at, conversation_id, member_id, last_read_at) "
                + "VALUES (:id, now(), :convId, :memberId, now())")
        .setParameter("id", UUID.randomUUID())
        .setParameter("convId", convId)
        .setParameter("memberId", userBId)
        .executeUpdate();

    em.flush();
    em.clear();

    return conversationRepository.findById(convId).orElseThrow();
  }
}
