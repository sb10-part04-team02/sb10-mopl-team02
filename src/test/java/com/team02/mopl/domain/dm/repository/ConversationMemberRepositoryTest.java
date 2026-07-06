package com.team02.mopl.domain.dm.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.dm.entity.ConversationMember;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ConversationMemberRepositoryTest extends RepositoryTestSupport {

  @Autowired private ConversationMemberRepository conversationMemberRepository;
  @Autowired private EntityManager em;

  private static final Instant BASE = Instant.parse("2026-07-02T00:00:00Z");

  private UUID memberId;

  @BeforeEach
  void setUp() {
    UUID userId = insertUser("user@test.com");
    UUID convId = insertConversation();
    memberId = insertMember(convId, userId, BASE);
  }

  @Test
  @DisplayName("새 시각이 기존 lastReadAt보다 크면 전진시키고 1을 반환한다")
  void advanceLastReadAt_later_updates() {
    Instant later = BASE.plusSeconds(60);

    int updated = conversationMemberRepository.advanceLastReadAt(memberId, later);
    em.clear();

    assertThat(updated).isEqualTo(1);
    assertThat(reloadLastReadAt()).isEqualTo(later);
  }

  @Test
  @DisplayName("새 시각이 기존 lastReadAt보다 작으면 갱신하지 않고 0을 반환한다")
  void advanceLastReadAt_earlier_doesNotUpdate() {
    Instant earlier = BASE.minusSeconds(60);

    int updated = conversationMemberRepository.advanceLastReadAt(memberId, earlier);
    em.clear();

    assertThat(updated).isEqualTo(0);
    assertThat(reloadLastReadAt()).isEqualTo(BASE);
  }

  @Test
  @DisplayName("새 시각이 기존 lastReadAt과 같으면 갱신하지 않고 0을 반환한다")
  void advanceLastReadAt_equal_doesNotUpdate() {
    int updated = conversationMemberRepository.advanceLastReadAt(memberId, BASE);
    em.clear();

    assertThat(updated).isEqualTo(0);
    assertThat(reloadLastReadAt()).isEqualTo(BASE);
  }

  private Instant reloadLastReadAt() {
    return conversationMemberRepository
        .findById(memberId)
        .map(ConversationMember::getLastReadAt)
        .orElseThrow();
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

  private UUID insertConversation() {
    UUID id = UUID.randomUUID();
    em.createNativeQuery("INSERT INTO conversations (id, created_at) VALUES (:id, now())")
        .setParameter("id", id)
        .executeUpdate();
    em.flush();
    return id;
  }

  private UUID insertMember(UUID convId, UUID userId, Instant lastReadAt) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO conversation_members (id, updated_at, conversation_id, member_id, last_read_at) "
                + "VALUES (:id, now(), :convId, :memberId, :lastReadAt)")
        .setParameter("id", id)
        .setParameter("convId", convId)
        .setParameter("memberId", userId)
        .setParameter("lastReadAt", lastReadAt)
        .executeUpdate();
    em.flush();
    em.clear();
    return id;
  }
}
