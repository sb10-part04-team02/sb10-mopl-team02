package com.team02.mopl.domain.subscription.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.subscription.entity.Subscription;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class SubscriptionRepositoryTest extends RepositoryTestSupport {

  @Autowired private SubscriptionRepository subscriptionRepository;

  @Autowired private EntityManager em;

  private UUID userId;
  private Playlist playlist;

  @BeforeEach
  void setUp() {
    userId = insertUser();
    playlist = insertPlaylist();
  }

  @Test
  @DisplayName("활성 구독이 존재하면 existsByUserIdAndPlaylist_IdAndDeletedAtIsNull이 true를 반환한다")
  void existsActiveSubscription_whenSubscriptionExists_returnsTrue() {
    subscriptionRepository.save(new Subscription(userId, playlist));
    em.flush();

    boolean exists =
        subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
            userId, playlist.getId());

    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("구독이 존재하지 않으면 existsByUserIdAndPlaylist_IdAndDeletedAtIsNull이 false를 반환한다")
  void existsActiveSubscription_whenSubscriptionDoesNotExist_returnsFalse() {
    boolean exists =
        subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
            userId, playlist.getId());

    assertThat(exists).isFalse();
  }

  @Test
  @DisplayName("소프트 삭제된 구독은 활성 중복 검사에서 제외되어 false를 반환한다")
  void existsActiveSubscription_whenSubscriptionSoftDeleted_returnsFalse() {
    Subscription subscription = subscriptionRepository.save(new Subscription(userId, playlist));
    em.flush();

    subscription.delete();
    em.flush();

    boolean exists =
        subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
            userId, playlist.getId());

    assertThat(exists).isFalse();
  }

  @Test
  @DisplayName("활성 상태에서 같은 (user_id, playlist_id)로 구독을 두 번 저장하면 부분 유니크 인덱스 위반 예외가 발생한다")
  void save_duplicateActiveUserAndPlaylist_violatesUniqueConstraint() {
    subscriptionRepository.save(new Subscription(userId, playlist));
    em.flush();

    subscriptionRepository.save(new Subscription(userId, playlist));

    assertThatThrownBy(() -> em.flush()).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("saveAndFlush는 활성 중복 구독을 저장할 때 즉시 부분 유니크 인덱스 위반 예외를 던진다")
  void saveAndFlush_duplicateActiveUserAndPlaylist_throwsImmediately() {
    subscriptionRepository.saveAndFlush(new Subscription(userId, playlist));

    assertThatThrownBy(
            () -> subscriptionRepository.saveAndFlush(new Subscription(userId, playlist)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("기존 구독을 소프트 삭제하면 같은 (user_id, playlist_id)로 새 구독을 생성할 수 있다")
  void save_afterSoftDelete_allowsReinsertWithSameKey() {
    Subscription first = subscriptionRepository.save(new Subscription(userId, playlist));
    em.flush();

    first.delete();
    em.flush();

    subscriptionRepository.save(new Subscription(userId, playlist));

    em.flush();

    assertThat(
            subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
                userId, playlist.getId()))
        .isTrue();
  }

  @Test
  @DisplayName("softDeleteActive는 활성 구독을 논리 삭제하고 1을 반환한다")
  void softDeleteActive_whenActiveSubscription_softDeletesAndReturnsOne() {
    subscriptionRepository.saveAndFlush(new Subscription(userId, playlist));

    int affected = subscriptionRepository.softDeleteActive(userId, playlist.getId(), Instant.now());
    em.flush();
    em.clear();

    assertThat(affected).isEqualTo(1);
    assertThat(
            subscriptionRepository.existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(
                userId, playlist.getId()))
        .isFalse();
  }

  @Test
  @DisplayName("softDeleteActive는 이미 삭제된 구독에 대해 0을 반환한다 (동시 취소 이중 감소 방지)")
  void softDeleteActive_whenAlreadyDeleted_returnsZero() {
    subscriptionRepository.saveAndFlush(new Subscription(userId, playlist));
    subscriptionRepository.softDeleteActive(userId, playlist.getId(), Instant.now());
    em.flush();
    em.clear();

    int affected = subscriptionRepository.softDeleteActive(userId, playlist.getId(), Instant.now());

    assertThat(affected).isZero();
  }

  @Test
  @DisplayName("softDeleteActive는 구독이 없으면 0을 반환한다")
  void softDeleteActive_whenNoSubscription_returnsZero() {
    int affected = subscriptionRepository.softDeleteActive(userId, playlist.getId(), Instant.now());

    assertThat(affected).isZero();
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", "구독자")
        .setParameter("email", "subscriber-" + id + "@test.com")
        .executeUpdate();
    return id;
  }

  private Playlist insertPlaylist() {
    Playlist created = new Playlist(userId, "테스트 플레이리스트", "설명");
    em.persist(created);
    em.flush();
    return created;
  }
}
