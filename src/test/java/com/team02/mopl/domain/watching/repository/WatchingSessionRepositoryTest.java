package com.team02.mopl.domain.watching.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.watching.entity.WatchingSession;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WatchingSessionRepositoryTest extends RepositoryTestSupport {

  @Autowired private WatchingSessionRepository watchingSessionRepository;
  @Autowired private EntityManager em;

  private UUID contentId;
  private UUID userId;

  @BeforeEach
  void setUp() {
    contentId = insertContent();
    userId = insertUser("테스트유저");
  }

  @Test
  @DisplayName("종료/삭제된 세션과 다른 콘텐츠의 세션은 제외하고 활성 세션만 반환한다")
  void findActiveSessionsByCursor_returnsOnlyActiveSessions() {
    // given
    // 활성 세션 - 결과에 포함되어야 함
    UUID active = insertSession(contentId, userId, Instant.parse("2026-06-29T01:00:00Z"));
    insertSession(
        contentId,
        insertUser("종료된시청자"),
        Instant.parse("2026-06-29T02:00:00Z"),
        Instant.parse("2026-06-29T03:00:00Z"),
        null); // 종료된 세션
    insertSession(
        contentId,
        insertUser("삭제된시청자"),
        Instant.parse("2026-06-29T02:30:00Z"),
        null,
        Instant.parse("2026-06-29T03:00:00Z")); // 논리 삭제된 세션
    insertSession(insertContent(), userId, Instant.parse("2026-06-29T02:40:00Z")); // 다른 콘텐츠

    // when
    List<WatchingSession> result =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, null, SortDirection.DESCENDING, null, null, 10);

    // then
    assertThat(result).extracting(WatchingSession::getId).containsExactly(active);
  }

  @Test
  @DisplayName("watcherNameLike는 시청자 이름을 대소문자 구분 없이 부분 일치로 필터링한다")
  void findActiveSessionsByCursor_filtersByWatcherNameIgnoringCase() {
    // given
    UUID alice = insertUser("Alice");
    UUID bob = insertUser("Bob");
    UUID aliceSession = insertSession(contentId, alice, Instant.parse("2026-06-29T01:00:00Z"));
    insertSession(contentId, bob, Instant.parse("2026-06-29T02:00:00Z"));

    // when
    List<WatchingSession> result =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, "lIC", SortDirection.DESCENDING, null, null, 10);

    // then - "lIc" -> "lic" 매칭되는지 확인
    assertThat(result).extracting(WatchingSession::getId).containsExactly(aliceSession);
  }

  @Test
  @DisplayName("watcherNameLike가 null이면 모든 활성 세션을 반환한다")
  void findActiveSessionsByCursor_withoutNameFilter_returnsAllActiveSessions() {
    // given
    insertSession(contentId, insertUser("Alice"), Instant.parse("2026-06-29T01:00:00Z"));
    insertSession(contentId, insertUser("Bob"), Instant.parse("2026-06-29T02:00:00Z"));

    // when
    List<WatchingSession> result =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, null, SortDirection.DESCENDING, null, null, 10);

    // then
    assertThat(result).hasSize(2);
  }

  @Test
  @DisplayName("내림차순 요청 시 createdAt 내림차순으로 반환한다")
  void findActiveSessionsByCursor_descending_returnsNewestFirst() {
    // given
    UUID s1 = insertSession(contentId, userId, Instant.parse("2026-06-29T01:00:00Z"));
    UUID s2 = insertSession(contentId, insertUser("시청자2"), Instant.parse("2026-06-29T02:00:00Z"));
    UUID s3 = insertSession(contentId, insertUser("시청자3"), Instant.parse("2026-06-29T03:00:00Z"));

    // when
    List<WatchingSession> result =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, null, SortDirection.DESCENDING, null, null, 10);

    // then
    assertThat(result).extracting(WatchingSession::getId).containsExactly(s3, s2, s1);
  }

  @Test
  @DisplayName("오름차순 요청 시 오래된 세션부터 반환한다")
  void findActiveSessionsByCursor_ascending_returnsOldestFirst() {
    // given
    UUID s1 = insertSession(contentId, userId, Instant.parse("2026-06-29T01:00:00Z"));
    UUID s2 = insertSession(contentId, insertUser("시청자2"), Instant.parse("2026-06-29T02:00:00Z"));

    // when
    List<WatchingSession> result =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, null, SortDirection.ASCENDING, null, null, 10);

    // then
    assertThat(result).extracting(WatchingSession::getId).containsExactly(s1, s2);
  }

  @Test
  @DisplayName("커서가 있으면 커서 이후의 다음 페이지를 반환한다")
  void findActiveSessionsByCursor_withCursor_returnsNextPage() {
    // given
    UUID s1 = insertSession(contentId, userId, Instant.parse("2026-06-29T01:00:00Z"));
    UUID s2 = insertSession(contentId, insertUser("시청자2"), Instant.parse("2026-06-29T02:00:00Z"));
    UUID s3 = insertSession(contentId, insertUser("시청자3"), Instant.parse("2026-06-29T03:00:00Z"));

    // when
    List<WatchingSession> result =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId,
            null,
            SortDirection.DESCENDING,
            Instant.parse("2026-06-29T03:00:00Z"),
            s3,
            10);

    // then
    assertThat(result).extracting(WatchingSession::getId).containsExactly(s2, s1);
  }

  @Test
  @DisplayName("동일 createdAt에서도 중복 없이 전체가 조회된다")
  void findActiveSessionsByCursor_sameCreatedAt_pagesCoverAllWithoutDuplicates() {
    // given
    Instant sameCreatedAt = Instant.parse("2026-06-29T01:00:00Z");
    UUID s1 = insertSession(contentId, userId, sameCreatedAt);
    UUID s2 = insertSession(contentId, insertUser("시청자2"), sameCreatedAt);
    UUID s3 = insertSession(contentId, insertUser("시청자3"), sameCreatedAt);
    UUID s4 = insertSession(contentId, insertUser("시청자4"), sameCreatedAt);

    // when - 첫 페이지(2건)를 조회하고, 그 마지막 항목을 커서로 다음 페이지를 조회한다
    List<WatchingSession> firstPage =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, null, SortDirection.DESCENDING, null, null, 2);
    WatchingSession last = firstPage.get(firstPage.size() - 1);
    List<WatchingSession> secondPage =
        watchingSessionRepository.findActiveSessionsByCursor(
            contentId, null, SortDirection.DESCENDING, last.getCreatedAt(), last.getId(), 10);

    // then - Postgres uuid 정렬은 Java UUID.compareTo와 다르므로 순서 대신 합집합으로 검증한다
    assertThat(firstPage).hasSize(2);
    assertThat(Stream.concat(firstPage.stream(), secondPage.stream()))
        .extracting(WatchingSession::getId)
        .containsExactlyInAnyOrder(s1, s2, s3, s4);
  }

  @Test
  @DisplayName("countActiveSessions는 종료/삭제된 세션을 제외하고 카운트한다")
  void countActiveSessions_countsOnlyActiveSessions() {
    // given
    insertSession(contentId, userId, Instant.parse("2026-06-29T01:00:00Z"));
    insertSession(contentId, insertUser("시청자2"), Instant.parse("2026-06-29T02:00:00Z"));
    insertSession(
        contentId,
        insertUser("종료된시청자"),
        Instant.parse("2026-06-29T03:00:00Z"),
        Instant.parse("2026-06-29T04:00:00Z"),
        null); // 종료된 세션
    insertSession(
        contentId,
        insertUser("삭제된시청자"),
        Instant.parse("2026-06-29T03:30:00Z"),
        null,
        Instant.parse("2026-06-29T04:00:00Z")); // 논리 삭제된 세션

    // when
    long count = watchingSessionRepository.countActiveSessions(contentId, null);

    // then
    assertThat(count).isEqualTo(2L);
  }

  @Test
  @DisplayName("countActiveSessions는 watcherNameLike 필터를 반영한다")
  void countActiveSessions_appliesNameFilter() {
    // given
    insertSession(contentId, insertUser("Alice"), Instant.parse("2026-06-29T01:00:00Z"));
    insertSession(contentId, insertUser("Bob"), Instant.parse("2026-06-29T02:00:00Z"));

    // when
    long count = watchingSessionRepository.countActiveSessions(contentId, "ali");

    // then
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName("이탈(종료+소프트 삭제)한 세션이 있어도 같은 유저·콘텐츠로 새 세션을 저장할 수 있다")
  void insert_afterExitAndSoftDelete_allowsRewatch() {
    // given: 이탈 처리된 세션(leave는 exited_at과 deleted_at을 함께 설정한다)
    insertSession(
        contentId,
        userId,
        Instant.parse("2026-06-29T01:00:00Z"),
        Instant.parse("2026-06-29T02:00:00Z"),
        Instant.parse("2026-06-29T02:00:00Z"));

    // when: 같은 (콘텐츠, 유저)로 재시청 세션 저장 - 부분 유니크 인덱스에 걸리지 않아야 한다
    UUID rewatch = insertSession(contentId, userId, Instant.parse("2026-06-29T03:00:00Z"));

    // then
    assertThat(watchingSessionRepository.countActiveByContentId(contentId)).isEqualTo(1L);
    assertThat(
            watchingSessionRepository.findByContent_IdAndUser_IdAndDeletedAtIsNull(
                contentId, userId))
        .hasValueSatisfying(session -> assertThat(session.getId()).isEqualTo(rewatch));
  }

  @Test
  @DisplayName("유저의 활성 세션이 없으면 empty를 반환한다")
  void findFirstByUser_noActiveSession_returnsEmpty() {
    // given: 종료+삭제된 세션만 존재
    insertSession(
        contentId,
        userId,
        Instant.parse("2026-06-29T01:00:00Z"),
        Instant.parse("2026-06-29T02:00:00Z"),
        Instant.parse("2026-06-29T02:00:00Z"));

    // when
    var result =
        watchingSessionRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  @DisplayName("유저가 여러 콘텐츠에서 활성 세션을 가지면 가장 최근 세션을 반환한다")
  void findFirstByUser_multipleActiveSessions_returnsMostRecent() {
    // given: 같은 유저가 서로 다른 콘텐츠에서 동시에 시청 중
    insertSession(contentId, userId, Instant.parse("2026-06-29T01:00:00Z"));
    UUID recent = insertSession(insertContent(), userId, Instant.parse("2026-06-29T03:00:00Z"));
    insertSession(insertContent(), userId, Instant.parse("2026-06-29T02:00:00Z"));

    // when
    var result =
        watchingSessionRepository.findFirstByUser_IdAndDeletedAtIsNullOrderByCreatedAtDesc(userId);

    // then
    assertThat(result).hasValueSatisfying(session -> assertThat(session.getId()).isEqualTo(recent));
  }

  private UUID insertUser(String name) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", name)
        .setParameter("email", "watcher-" + id + "@test.com")
        .executeUpdate();
    return id;
  }

  private UUID insertContent() {
    Content content = new Content(ContentType.MOVIE, "테스트 영화", "설명", "http://img");
    em.persist(content);
    em.flush();
    return content.getId();
  }

  private UUID insertSession(UUID contentId, UUID userId, Instant createdAt) {
    return insertSession(contentId, userId, createdAt, null, null);
  }

  private UUID insertSession(
      UUID contentId, UUID userId, Instant createdAt, Instant exitedAt, Instant deletedAt) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO watching_sessions "
                + "(id, created_at, updated_at, deleted_at, content_id, user_id, joined_at, exited_at) "
                + "VALUES (:id, :createdAt, now(), :deletedAt, :contentId, :userId, :createdAt, :exitedAt)")
        .setParameter("id", id)
        .setParameter("createdAt", createdAt)
        .setParameter("deletedAt", deletedAt)
        .setParameter("contentId", contentId)
        .setParameter("userId", userId)
        .setParameter("exitedAt", exitedAt)
        .executeUpdate();
    em.flush();
    return id;
  }
}
