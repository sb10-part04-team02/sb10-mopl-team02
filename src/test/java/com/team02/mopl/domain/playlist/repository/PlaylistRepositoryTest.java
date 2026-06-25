package com.team02.mopl.domain.playlist.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.content.ContentType;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.entity.PlaylistContent;
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

    List<PlaylistContent> result = playlistContentRepository.findByPlaylistId(playlist.getId());

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
