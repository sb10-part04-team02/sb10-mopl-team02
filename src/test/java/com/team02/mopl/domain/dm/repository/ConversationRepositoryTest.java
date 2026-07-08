package com.team02.mopl.domain.dm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.global.enums.SortDirection;
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
            userId, null, SortDirection.DESCENDING, null, null, 10);

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
            userId, null, SortDirection.ASCENDING, null, null, 10);

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
            userId, null, SortDirection.DESCENDING, conv3.getCreatedAt(), conv3.getId(), 10);

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
            userId, null, SortDirection.DESCENDING, null, null, 10);

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
            userId, null, SortDirection.DESCENDING, null, null, 2);

    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("keyword가 있으면 상대방 이름에 부분일치하는 대화만 반환한다")
  void findConversationsByCursor_withKeyword_filtersByCounterpartName() {
    UUID kimId = insertUser("kim@test.com", "김철수");
    UUID leeId = insertUser("lee@test.com", "이영희");
    Conversation convWithKim =
        saveConversationWithMembers(userId, kimId, Instant.parse("2026-06-29T01:00:00Z"));
    saveConversationWithMembers(userId, leeId, Instant.parse("2026-06-29T02:00:00Z"));

    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, "철수", SortDirection.DESCENDING, null, null, 10);

    assertThat(result).extracting(Conversation::getId).containsExactly(convWithKim.getId());
  }

  @Test
  @DisplayName("keyword가 자신의 이름에만 일치하면 대화를 반환하지 않는다")
  void findConversationsByCursor_keywordMatchingOwnNameOnly_returnsEmpty() {
    UUID kimId = insertUser("kim@test.com", "김철수");
    saveConversationWithMembers(userId, kimId, Instant.parse("2026-06-29T01:00:00Z"));

    // 요청자(userId)의 이름은 "테스트유저" — 자신의 이름 검색은 상대방 이름과 일치하지 않으므로 제외된다
    List<Conversation> result =
        conversationRepository.findConversationsByCursor(
            userId, "테스트유저", SortDirection.DESCENDING, null, null, 10);

    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("keyword가 있으면 상대방 이름에 부분일치하는 활성 대화 수만 센다")
  void countByMemberUserId_withKeyword_countsMatchingOnly() {
    UUID kimId = insertUser("kim@test.com", "김철수");
    UUID leeId = insertUser("lee@test.com", "이영희");
    saveConversationWithMembers(userId, kimId, Instant.parse("2026-06-29T01:00:00Z"));
    saveConversationWithMembers(userId, leeId, Instant.parse("2026-06-29T02:00:00Z"));

    long count = conversationRepository.countByMemberUserId(userId, "철수");

    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("사용자의 활성 대화 수를 반환한다")
  void countByMemberUserId_returnsActiveConversationCount() {
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T01:00:00Z"));
    saveConversationWithMembers(userId, otherUserId, Instant.parse("2026-06-29T02:00:00Z"));

    UUID thirdUserId = insertUser("third@test.com");
    saveConversationWithMembers(otherUserId, thirdUserId, Instant.parse("2026-06-29T03:00:00Z"));

    long count = conversationRepository.countByMemberUserId(userId, null);

    assertThat(count).isEqualTo(2L);
  }

  private UUID insertUser(String email) {
    return insertUser(email, "테스트유저");
  }

  private UUID insertUser(String email, String name) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", name)
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
