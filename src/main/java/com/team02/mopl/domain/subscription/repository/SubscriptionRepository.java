package com.team02.mopl.domain.subscription.repository;

import com.team02.mopl.domain.subscription.entity.Subscription;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  boolean existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(UUID userId, UUID playlistId);

  Optional<Subscription> findByUserIdAndPlaylist_IdAndDeletedAtIsNull(UUID userId, UUID playlistId);

  // 요청자가 구독 중인 플레이리스트 id만 골라 반환 (목록 조회 subscribedByMe N+1 방지)
  @Query(
      "SELECT s.playlist.id FROM Subscription s "
          + "WHERE s.userId = :userId AND s.deletedAt IS NULL "
          + "AND s.playlist.id IN :playlistIds")
  List<UUID> findSubscribedPlaylistIds(
      @Param("userId") UUID userId, @Param("playlistIds") List<UUID> playlistIds);

  List<Subscription> findByPlaylist_IdAndDeletedAtIsNull(UUID playlistId);
}
