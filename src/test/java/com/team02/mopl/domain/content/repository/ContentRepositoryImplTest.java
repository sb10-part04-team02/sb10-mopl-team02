package com.team02.mopl.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.dto.ContentSearchCondition;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@DisplayName("ContentRepositoryImpl(QueryDSL) 통합 테스트")
class ContentRepositoryImplTest extends RepositoryTestSupport {

  @Autowired private ContentRepository contentRepository;

  @Autowired private EntityManager em;

  private static final Instant BASE = Instant.parse("2026-06-30T00:00:00Z");

  // 정렬/커서 검증을 위해 created_at, average_rating을 명시 지정한 활성 콘텐츠를 삽입한다.
  private UUID insertContent(String title, ContentType type, Instant createdAt, double rating) {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO contents (id, created_at, updated_at, content_type, title, description, "
                + "thumbnail_url, average_rating, review_count) "
                + "VALUES (:id, :createdAt, :updatedAt, :type, :title, :desc, :thumb, :rating, 0)")
        .setParameter("id", id)
        .setParameter("createdAt", createdAt)
        .setParameter("updatedAt", createdAt)
        .setParameter("type", type.name())
        .setParameter("title", title)
        .setParameter("desc", title + " 설명")
        .setParameter("thumb", "http://img")
        .setParameter("rating", rating)
        .executeUpdate();
    return id;
  }

  private void softDeleteContent(UUID contentId) {
    em.createNativeQuery("UPDATE contents SET deleted_at = now() WHERE id = :id")
        .setParameter("id", contentId)
        .executeUpdate();
  }

  private void insertTag(UUID contentId, String name, boolean deleted) {
    em.createNativeQuery(
            "INSERT INTO tags (id, created_at, deleted_at, content_id, name) "
                + "VALUES (:id, now(), :deletedAt, :cid, :name)")
        .setParameter("id", UUID.randomUUID())
        .setParameter("deletedAt", deleted ? Instant.now() : null)
        .setParameter("cid", contentId)
        .setParameter("name", name)
        .executeUpdate();
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", "시청자")
        .setParameter("email", "watcher-" + id + "@test.com")
        .executeUpdate();
    return id;
  }

  // exitedAt/deletedAt 조합으로 활성 여부를 제어하는 시청 세션 삽입
  private void insertSession(UUID contentId, UUID userId, boolean exited, boolean deleted) {
    em.createNativeQuery(
            "INSERT INTO watching_sessions (id, created_at, updated_at, content_id, user_id, "
                + "joined_at, exited_at, deleted_at) "
                + "VALUES (:id, now(), now(), :cid, :uid, now(), :exitedAt, :deletedAt)")
        .setParameter("id", UUID.randomUUID())
        .setParameter("cid", contentId)
        .setParameter("uid", userId)
        .setParameter("exitedAt", exited ? Instant.now() : null)
        .setParameter("deletedAt", deleted ? Instant.now() : null)
        .executeUpdate();
  }

  private ContentSearchCondition condition(
      ContentType type,
      String keyword,
      List<String> tags,
      SortBy sortBy,
      boolean asc,
      Comparable<?> cursor,
      UUID idAfter,
      int limit) {
    return new ContentSearchCondition(
        type, keyword, tags == null ? List.of() : tags, sortBy, asc, cursor, idAfter, limit);
  }

  // 필터/커서 없이 createdAt DESC 정렬만 적용하는 기본 조건
  private ContentSearchCondition latestFirst(int limit) {
    return condition(null, null, List.of(), SortBy.CREATED_AT, false, null, null, limit);
  }

  @Nested
  @DisplayName("동적 필터")
  class Filters {

    @Test
    @DisplayName("type을 지정하면 해당 타입의 콘텐츠만 반환하고, null이면 모든 타입을 반환한다")
    void filtersByType() {
      // given
      UUID movie = insertContent("영화", ContentType.MOVIE, BASE, 0.0);
      UUID tv = insertContent("드라마", ContentType.TV_SERIES, BASE, 0.0);

      // when
      List<Content> onlyMovie =
          contentRepository.search(
              condition(ContentType.MOVIE, null, null, SortBy.CREATED_AT, false, null, null, 10));
      List<Content> all = contentRepository.search(latestFirst(10));

      // then
      assertThat(onlyMovie).extracting(Content::getId).containsExactly(movie);
      assertThat(all).extracting(Content::getId).containsExactlyInAnyOrder(movie, tv);
    }

    @Test
    @DisplayName("키워드는 제목 또는 설명에 대소문자 구분 없이 부분 매칭된다")
    void filtersByKeywordIgnoreCase() {
      // given
      UUID inception = insertContent("Inception", ContentType.MOVIE, BASE, 0.0);
      insertContent("기타 영화", ContentType.MOVIE, BASE, 0.0);

      // when: 제목 부분 + 소문자 매칭 / 설명("Inception 설명") + 대문자 매칭
      List<Content> byTitle =
          contentRepository.search(
              condition(null, "incep", null, SortBy.CREATED_AT, false, null, null, 10));
      List<Content> byDescription =
          contentRepository.search(
              condition(null, "INCEPTION 설명", null, SortBy.CREATED_AT, false, null, null, 10));

      // then
      assertThat(byTitle).extracting(Content::getId).containsExactly(inception);
      assertThat(byDescription).extracting(Content::getId).containsExactly(inception);
    }

    @Test
    @DisplayName("태그 필터는 활성 태그를 하나라도 가진 콘텐츠만 반환하고 삭제된 태그는 무시한다")
    void filtersByTagsExcludingDeleted() {
      // given
      UUID a = insertContent("A", ContentType.MOVIE, BASE, 0.0);
      UUID b = insertContent("B", ContentType.MOVIE, BASE, 0.0);
      UUID c = insertContent("C", ContentType.MOVIE, BASE, 0.0);
      insertTag(a, "액션", false);
      insertTag(b, "드라마", false);
      insertTag(c, "액션", true); // 삭제된 태그라 매칭되지 않아야 함

      // when
      List<Content> result =
          contentRepository.search(
              condition(null, null, List.of("액션"), SortBy.CREATED_AT, false, null, null, 10));

      // then
      assertThat(result).extracting(Content::getId).containsExactly(a);
    }

    @Test
    @DisplayName("논리 삭제된 콘텐츠는 결과에서 제외된다")
    void excludesSoftDeletedContent() {
      // given
      UUID alive = insertContent("살아있음", ContentType.MOVIE, BASE, 0.0);
      UUID deleted = insertContent("삭제됨", ContentType.MOVIE, BASE, 0.0);
      softDeleteContent(deleted);

      // when
      List<Content> result = contentRepository.search(latestFirst(10));

      // then
      assertThat(result).extracting(Content::getId).containsExactly(alive);
    }
  }

  @Nested
  @DisplayName("동적 정렬")
  class Sorting {

    @Test
    @DisplayName("CREATED_AT DESC는 최신순으로 정렬한다")
    void sortsByCreatedAtDesc() {
      // given
      UUID oldest = insertContent("c3", ContentType.MOVIE, BASE, 0.0);
      UUID middle = insertContent("c2", ContentType.MOVIE, BASE.plusSeconds(60), 0.0);
      UUID newest = insertContent("c1", ContentType.MOVIE, BASE.plusSeconds(120), 0.0);

      // when
      List<Content> result =
          contentRepository.search(
              condition(null, null, null, SortBy.CREATED_AT, false, null, null, 10));

      // then
      assertThat(result).extracting(Content::getId).containsExactly(newest, middle, oldest);
    }

    @Test
    @DisplayName("RATE ASC는 평점 오름차순으로 정렬한다")
    void sortsByRateAsc() {
      // given
      UUID low = insertContent("low", ContentType.MOVIE, BASE, 2.0);
      UUID mid = insertContent("mid", ContentType.MOVIE, BASE, 4.0);
      UUID high = insertContent("high", ContentType.MOVIE, BASE, 5.0);

      // when
      List<Content> result =
          contentRepository.search(condition(null, null, null, SortBy.RATE, true, null, null, 10));

      // then
      assertThat(result).extracting(Content::getId).containsExactly(low, mid, high);
    }

    @Test
    @DisplayName("WATCHER_COUNT DESC는 활성 시청 세션 수 기준으로 정렬하며 종료/삭제 세션은 제외한다")
    void sortsByWatcherCountDesc() {
      // given
      UUID two = insertContent("two", ContentType.MOVIE, BASE, 0.0);
      UUID one = insertContent("one", ContentType.MOVIE, BASE, 0.0);
      UUID zero = insertContent("zero", ContentType.MOVIE, BASE, 0.0);
      UUID u1 = insertUser();
      UUID u2 = insertUser();
      insertSession(two, u1, false, false); // 활성
      insertSession(two, u2, false, false); // 활성 -> two: 2
      insertSession(one, u1, false, false); // 활성 -> one: 1
      insertSession(zero, u1, true, false); // 종료 -> 제외
      insertSession(zero, u2, false, true); // 삭제 -> 제외 -> zero: 0

      // when
      List<Content> result =
          contentRepository.search(
              condition(null, null, null, SortBy.WATCHER_COUNT, false, null, null, 10));

      // then
      assertThat(result).extracting(Content::getId).containsExactly(two, one, zero);
    }
  }

  @Nested
  @DisplayName("커서 페이지네이션")
  class Cursor {

    @Test
    @DisplayName("cursor가 null이면 첫 페이지로 limit 건을 반환한다")
    void firstPage_whenCursorNull() {
      // given
      insertContent("c3", ContentType.MOVIE, BASE, 0.0);
      insertContent("c2", ContentType.MOVIE, BASE.plusSeconds(60), 0.0);
      insertContent("c1", ContentType.MOVIE, BASE.plusSeconds(120), 0.0);

      // when
      List<Content> firstPage = contentRepository.search(latestFirst(2));

      // then
      assertThat(firstPage).hasSize(2);
    }

    @Test
    @DisplayName("CREATED_AT DESC에서 커서(createdAt+idAfter) 이후 행만 반환한다")
    void returnsRowsAfterCursor() {
      // given
      UUID c3 = insertContent("c3", ContentType.MOVIE, BASE, 0.0);
      UUID c2 = insertContent("c2", ContentType.MOVIE, BASE.plusSeconds(60), 0.0);
      UUID c1 = insertContent("c1", ContentType.MOVIE, BASE.plusSeconds(120), 0.0);

      // when: c1을 받은 뒤 c1을 커서로 다음 페이지 조회
      List<Content> next =
          contentRepository.search(
              condition(null, null, null, SortBy.CREATED_AT, false, BASE.plusSeconds(120), c1, 10));

      // then: c1 제외, c2, c3 순
      assertThat(next).extracting(Content::getId).containsExactly(c2, c3);
    }

    @Test
    @DisplayName("RATE DESC에서 커서(averageRating+idAfter) 이후 행만 반환한다")
    void returnsRowsAfterCursor_whenRateDesc() {
      // given
      insertContent("high", ContentType.MOVIE, BASE, 5.0);
      UUID mid = insertContent("mid", ContentType.MOVIE, BASE, 4.0);
      UUID low = insertContent("low", ContentType.MOVIE, BASE, 3.0);

      // when: mid(4.0)를 커서로 다음 페이지 조회
      List<Content> next =
          contentRepository.search(condition(null, null, null, SortBy.RATE, false, 4.0, mid, 10));

      // then
      assertThat(next).extracting(Content::getId).containsExactly(low);
    }

    @Test
    @DisplayName("ASC 방향에서는 커서보다 정렬 키가 큰 행만 반환한다")
    void returnsRowsAfterCursor_whenAscDirection() {
      // given
      insertContent("low", ContentType.MOVIE, BASE, 2.0);
      UUID mid = insertContent("mid", ContentType.MOVIE, BASE, 4.0);
      UUID high = insertContent("high", ContentType.MOVIE, BASE, 5.0);

      // when: RATE ASC에서 mid(4.0)를 커서로 다음 페이지 조회
      List<Content> next =
          contentRepository.search(condition(null, null, null, SortBy.RATE, true, 4.0, mid, 10));

      // then
      assertThat(next).extracting(Content::getId).containsExactly(high);
    }

    @Test
    @DisplayName("WATCHER_COUNT DESC에서 커서(시청자 수+idAfter) 이후 행만 반환한다")
    void returnsRowsAfterCursor_whenWatcherCountDesc() {
      // given
      UUID two = insertContent("two", ContentType.MOVIE, BASE, 0.0);
      UUID one = insertContent("one", ContentType.MOVIE, BASE, 0.0);
      UUID zero = insertContent("zero", ContentType.MOVIE, BASE, 0.0);
      UUID u1 = insertUser();
      UUID u2 = insertUser();
      insertSession(two, u1, false, false);
      insertSession(two, u2, false, false); // two: 2
      insertSession(one, u1, false, false); // one: 1 -> zero: 0

      // when: two(시청자 2명)를 커서로 다음 페이지 조회
      List<Content> next =
          contentRepository.search(
              condition(null, null, null, SortBy.WATCHER_COUNT, false, 2L, two, 10));

      // then
      assertThat(next).extracting(Content::getId).containsExactly(one, zero);
    }

    @Test
    @DisplayName("정렬 키가 동률이어도 id tie-break로 전 페이지 순회 시 누락/중복이 없다")
    void paginatesAllRowsWithoutLossOrDuplication_whenSortKeyTies() {
      // given: 4.0 동률 3건을 포함한 5건
      List<UUID> inserted =
          List.of(
              insertContent("r5", ContentType.MOVIE, BASE, 5.0),
              insertContent("r4a", ContentType.MOVIE, BASE, 4.0),
              insertContent("r4b", ContentType.MOVIE, BASE, 4.0),
              insertContent("r4c", ContentType.MOVIE, BASE, 4.0),
              insertContent("r3", ContentType.MOVIE, BASE, 3.0));

      // when: RATE DESC, 페이지 크기 2로 끝 페이지까지 순회
      List<Content> collected = new ArrayList<>();
      Comparable<?> cursor = null;
      UUID idAfter = null;
      while (true) {
        List<Content> page =
            contentRepository.search(
                condition(null, null, null, SortBy.RATE, false, cursor, idAfter, 2));
        collected.addAll(page);
        if (page.size() < 2) {
          break;
        }
        Content last = page.get(page.size() - 1);
        cursor = last.getAverageRating();
        idAfter = last.getId();
      }

      // then: 누락/중복 없이 전체 5건, 평점은 비증가
      // (동률 간 순서는 Postgres uuid 정렬이 Java UUID.compareTo와 달라 id 순서는 단언하지 않는다)
      assertThat(collected)
          .extracting(Content::getId)
          .containsExactlyInAnyOrderElementsOf(inserted)
          .doesNotHaveDuplicates();
      assertThat(collected.stream().map(Content::getAverageRating).toList())
          .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    @DisplayName("limit는 limit+1 적재(hasNext 판정)를 위해 요청 건수만큼 반환한다")
    void honorsLimit() {
      // given
      insertContent("c3", ContentType.MOVIE, BASE, 0.0);
      insertContent("c2", ContentType.MOVIE, BASE.plusSeconds(60), 0.0);
      insertContent("c1", ContentType.MOVIE, BASE.plusSeconds(120), 0.0);

      // when & then: fetchLimit=3이면 3건, 페이지 크기보다 많으면 limit 만큼만 반환
      assertThat(contentRepository.search(latestFirst(3))).hasSize(3);
      assertThat(contentRepository.search(latestFirst(2))).hasSize(2);
    }
  }

  @Nested
  @DisplayName("countBySearch")
  class CountBySearch {

    @Test
    @DisplayName("필터를 반영하되 커서는 무시하고 전체 건수를 센다")
    void countsWithFilterIgnoringCursor() {
      // given
      insertContent("영화1", ContentType.MOVIE, BASE, 0.0);
      insertContent("영화2", ContentType.MOVIE, BASE.plusSeconds(60), 0.0);
      insertContent("드라마", ContentType.TV_SERIES, BASE, 0.0);

      // when: 커서가 있어도 count에는 영향 없음
      long count =
          contentRepository.countBySearch(
              condition(
                  ContentType.MOVIE,
                  null,
                  null,
                  SortBy.CREATED_AT,
                  false,
                  BASE,
                  UUID.randomUUID(),
                  1));

      // then: MOVIE 2건
      assertThat(count).isEqualTo(2L);
    }

    @Test
    @DisplayName("조건에 맞는 콘텐츠가 없으면 0을 반환한다")
    void countsZero_whenNoMatch() {
      // given
      insertContent("영화", ContentType.MOVIE, BASE, 0.0);

      // when
      long count =
          contentRepository.countBySearch(
              condition(ContentType.SPORT, null, null, SortBy.CREATED_AT, false, null, null, 10));

      // then
      assertThat(count).isZero();
    }
  }
}
