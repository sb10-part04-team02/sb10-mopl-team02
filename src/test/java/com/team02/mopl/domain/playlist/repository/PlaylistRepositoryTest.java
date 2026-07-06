package com.team02.mopl.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.entity.PlaylistContent;
import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

class PlaylistRepositoryTest extends RepositoryTestSupport {

  @Autowired private PlaylistRepository playlistRepository;
  @Autowired private PlaylistContentRepository playlistContentRepository;

  @PersistenceContext private EntityManager em;

  private UUID ownerId;
  private UUID contentId;

  @BeforeEach
  void setUp() {
    ownerId = insertUser();
    contentId = insertContent();
  }

  @Test
  @DisplayName("플레이리스트를 저장하면 소유자 ID로 조회된다")
  void findByOwnerId_whenPlaylistExists_returnsPlaylist() {
    playlistRepository.save(new Playlist(ownerId, "내 플레이리스트", "설명"));
    em.flush();

    List<Playlist> result = playlistRepository.findByOwnerIdAndDeletedAtIsNull(ownerId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getTitle()).isEqualTo("내 플레이리스트");
    assertThat(result.get(0).getSubscriberCount()).isZero();
  }

  @Test
  @DisplayName("소프트 삭제된 플레이리스트는 소유자 조회에서 제외된다")
  void findByOwnerId_whenSoftDeleted_isExcluded() {
    Playlist playlist = playlistRepository.save(new Playlist(ownerId, "삭제될 리스트", "설명"));
    em.flush();

    playlist.delete();
    em.flush();

    assertThat(playlistRepository.findByOwnerIdAndDeletedAtIsNull(ownerId)).isEmpty();
  }

  @Test
  @DisplayName("플레이리스트에 콘텐츠를 추가하면 플레이리스트 ID로 조회된다")
  void addContent_whenSaved_isFoundByPlaylistId() {
    Playlist playlist = playlistRepository.save(new Playlist(ownerId, "리스트", "설명"));
    playlistContentRepository.save(new PlaylistContent(playlist, contentId));
    em.flush();

    List<PlaylistContent> result =
        playlistContentRepository.findByPlaylistIdOrderByCreatedAtAscIdAsc(playlist.getId());

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getContentId()).isEqualTo(contentId);
  }

  @Test
  @DisplayName("같은 플레이리스트에 같은 콘텐츠를 두 번 추가하면 유니크 제약 위반 예외가 발생한다")
  void addContent_whenDuplicate_violatesUniqueConstraint() {
    Playlist playlist = playlistRepository.save(new Playlist(ownerId, "리스트", "설명"));
    playlistContentRepository.save(new PlaylistContent(playlist, contentId));
    em.flush();

    playlistContentRepository.save(new PlaylistContent(playlist, contentId));

    assertThatThrownBy(() -> em.flush()).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("deleteByPlaylistIdAndContentId로 특정 콘텐츠를 플레이리스트에서 제거할 수 있다")
  void deleteByPlaylistIdAndContentId_removesContent() {
    Playlist playlist = playlistRepository.save(new Playlist(ownerId, "리스트", "설명"));
    playlistContentRepository.save(new PlaylistContent(playlist, contentId));
    em.flush();

    playlistContentRepository.deleteByPlaylistIdAndContentId(playlist.getId(), contentId);
    em.flush();

    assertThat(
            playlistContentRepository.existsByPlaylistIdAndContentId(playlist.getId(), contentId))
        .isFalse();
  }

  @Test
  @DisplayName("커서 조회는 논리 삭제 플레이리스트를 제외하고 updatedAt 내림차순으로 반환한다")
  void findPlaylistsByCursor_excludesSoftDeleted_andSortsByUpdatedAtDesc() {
    Playlist first = playlistRepository.save(new Playlist(ownerId, "첫 번째", "설명"));
    em.flush();
    Playlist second = playlistRepository.save(new Playlist(ownerId, "두 번째", "설명"));
    em.flush();
    Playlist deleted = playlistRepository.save(new Playlist(ownerId, "삭제됨", "설명"));
    deleted.delete();
    em.flush();

    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null, PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING, null, null, 10);

    assertThat(result).extracting(Playlist::getId).containsExactly(second.getId(), first.getId());
  }

  @Test
  @DisplayName("keyword가 있으면 제목·설명을 대소문자 구분 없이 부분일치로 필터링한다")
  void findPlaylistsByCursor_filtersByKeyword() {
    playlistRepository.save(new Playlist(ownerId, "액션 영화 모음", "설명"));
    playlistRepository.save(new Playlist(ownerId, "다른 리스트", "액션 태그 포함"));
    playlistRepository.save(new Playlist(ownerId, "코미디", "웃긴 것"));
    em.flush();

    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            "액션", PlaylistSortBy.UPDATED_AT, SortDirection.DESCENDING, null, null, 10);

    assertThat(result).hasSize(2);
    assertThat(countActiveWithKeyword("액션")).isEqualTo(2L);
    assertThat(countActiveWithKeyword("코미디")).isEqualTo(1L);
  }

  @Test
  @DisplayName("커서 이후 항목만 반환한다 (updatedAt DESC, 복합키 경계)")
  void findPlaylistsByCursor_returnsItemsAfterCursor() {
    Playlist first = playlistRepository.save(new Playlist(ownerId, "첫 번째", "설명"));
    em.flush();
    Playlist second = playlistRepository.save(new Playlist(ownerId, "두 번째", "설명"));
    em.flush();

    // second를 커서로 넘기면 그 이후(더 과거)인 first만 남는다
    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null,
            PlaylistSortBy.UPDATED_AT,
            SortDirection.DESCENDING,
            second.getUpdatedAt(),
            second.getId(),
            10);

    assertThat(result).extracting(Playlist::getId).containsExactly(first.getId());
  }

  @Test
  @DisplayName("subscriberCount 내림차순으로 정렬해 반환한다")
  void findPlaylistsByCursor_sortsBySubscriberCountDesc() {
    savePlaylistWithSubscriberCount("적음", 1L);
    savePlaylistWithSubscriberCount("많음", 5L);
    savePlaylistWithSubscriberCount("중간", 3L);
    em.flush();

    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING, null, null, 10);

    assertThat(result).extracting(Playlist::getSubscriberCount).containsExactly(5L, 3L, 1L);
  }

  @Test
  @DisplayName("subscriberCount 커서 이후 항목만 반환한다 (DESC, 복합키 경계)")
  void findPlaylistsByCursor_bySubscriberCount_returnsItemsAfterCursor() {
    Playlist high = savePlaylistWithSubscriberCount("높음", 5L);
    Playlist mid = savePlaylistWithSubscriberCount("중간", 3L);
    Playlist low = savePlaylistWithSubscriberCount("낮음", 1L);
    em.flush();

    // 중간(3)을 커서로 넘기면 그 이후(더 작은 subscriberCount)인 낮음(1)만 남는다
    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null,
            PlaylistSortBy.SUBSCRIBE_COUNT,
            SortDirection.DESCENDING,
            mid.getSubscriberCount(),
            mid.getId(),
            10);

    assertThat(result).extracting(Playlist::getId).containsExactly(low.getId());
    assertThat(result).doesNotContain(high);
  }

  @Test
  @DisplayName("subscriberCount 동률이면 id 타이브레이커로 커서 이후 항목만 반환한다")
  void findPlaylistsByCursor_bySubscriberCount_breaksTiesById() {
    savePlaylistWithSubscriberCount("동률 A", 2L);
    savePlaylistWithSubscriberCount("동률 B", 2L);
    em.flush();

    // DB 정렬 순서(UUID 비교는 Java와 다를 수 있으므로 실제 조회 결과로 확인)에서
    // 첫 항목을 커서로 넘기면 그 이후인 두 번째 항목만 남는다
    List<Playlist> all =
        playlistRepository.findPlaylistsByCursor(
            null, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.DESCENDING, null, null, 10);
    assertThat(all).hasSize(2);
    Playlist cursor = all.get(0);
    Playlist remaining = all.get(1);

    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null,
            PlaylistSortBy.SUBSCRIBE_COUNT,
            SortDirection.DESCENDING,
            cursor.getSubscriberCount(),
            cursor.getId(),
            10);

    assertThat(result).extracting(Playlist::getId).containsExactly(remaining.getId());
  }

  @Test
  @DisplayName("updatedAt 오름차순이면 오래된 플레이리스트부터 반환한다")
  void findPlaylistsByCursor_sortsByUpdatedAtAsc() {
    Playlist first = playlistRepository.save(new Playlist(ownerId, "첫 번째", "설명"));
    em.flush();
    Playlist second = playlistRepository.save(new Playlist(ownerId, "두 번째", "설명"));
    em.flush();

    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null, PlaylistSortBy.UPDATED_AT, SortDirection.ASCENDING, null, null, 10);

    // ASC: 오래된 first가 먼저, 최신 second가 나중
    assertThat(result).extracting(Playlist::getId).containsExactly(first.getId(), second.getId());
  }

  @Test
  @DisplayName("커서 이후 항목만 반환한다 (updatedAt ASC, 복합키 경계)")
  void findPlaylistsByCursor_byUpdatedAt_asc_returnsItemsAfterCursor() {
    Playlist first = playlistRepository.save(new Playlist(ownerId, "첫 번째", "설명"));
    em.flush();
    Playlist second = playlistRepository.save(new Playlist(ownerId, "두 번째", "설명"));
    em.flush();

    // ASC에서 first(더 과거)를 커서로 넘기면 그 이후(더 미래)인 second만 남는다
    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null,
            PlaylistSortBy.UPDATED_AT,
            SortDirection.ASCENDING,
            first.getUpdatedAt(),
            first.getId(),
            10);

    assertThat(result).extracting(Playlist::getId).containsExactly(second.getId());
  }

  @Test
  @DisplayName("subscriberCount 오름차순으로 정렬해 반환한다")
  void findPlaylistsByCursor_sortsBySubscriberCountAsc() {
    savePlaylistWithSubscriberCount("적음", 1L);
    savePlaylistWithSubscriberCount("많음", 5L);
    savePlaylistWithSubscriberCount("중간", 3L);
    em.flush();

    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null, PlaylistSortBy.SUBSCRIBE_COUNT, SortDirection.ASCENDING, null, null, 10);

    assertThat(result).extracting(Playlist::getSubscriberCount).containsExactly(1L, 3L, 5L);
  }

  @Test
  @DisplayName("subscriberCount 커서 이후 항목만 반환한다 (ASC, 복합키 경계)")
  void findPlaylistsByCursor_bySubscriberCount_asc_returnsItemsAfterCursor() {
    Playlist low = savePlaylistWithSubscriberCount("낮음", 1L);
    Playlist mid = savePlaylistWithSubscriberCount("중간", 3L);
    Playlist high = savePlaylistWithSubscriberCount("높음", 5L);
    em.flush();

    // ASC에서 중간(3)을 커서로 넘기면 그 이후(더 큰 subscriberCount)인 높음(5)만 남는다
    List<Playlist> result =
        playlistRepository.findPlaylistsByCursor(
            null,
            PlaylistSortBy.SUBSCRIBE_COUNT,
            SortDirection.ASCENDING,
            mid.getSubscriberCount(),
            mid.getId(),
            10);

    assertThat(result).extracting(Playlist::getId).containsExactly(high.getId());
    assertThat(result).doesNotContain(low);
  }

  private Playlist savePlaylistWithSubscriberCount(String title, long subscriberCount) {
    Playlist playlist = new Playlist(ownerId, title, "설명");
    ReflectionTestUtils.setField(playlist, "subscriberCount", subscriberCount);
    return playlistRepository.save(playlist);
  }

  private long countActiveWithKeyword(String keyword) {
    return playlistRepository.countActive(keyword);
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", "소유자")
        .setParameter("email", "owner-" + id + "@test.com")
        .executeUpdate();
    return id;
  }

  private UUID insertContent() {
    Content content = new Content(ContentType.MOVIE, "테스트 영화", "설명", "http://img");
    em.persist(content);
    em.flush();
    return content.getId();
  }
}
