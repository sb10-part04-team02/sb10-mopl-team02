package com.team02.mopl.domain.playlist.repository;

import com.team02.mopl.domain.playlist.entity.Playlist;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlaylistRepository
    extends JpaRepository<Playlist, UUID>, PlaylistRepositoryCustom {

  List<Playlist> findByOwnerIdAndDeletedAtIsNull(UUID ownerId);

  Optional<Playlist> findByIdAndDeletedAtIsNull(UUID id);

  // 구독자 수만 증감
  @Modifying
  @Query("UPDATE Playlist p SET p.subscriberCount = p.subscriberCount + 1 WHERE p.id = :id")
  void increaseSubscriberCount(@Param("id") UUID id);

  @Modifying
  @Query(
      "UPDATE Playlist p SET p.subscriberCount = p.subscriberCount - 1 "
          + "WHERE p.id = :id AND p.subscriberCount > 0")
  void decreaseSubscriberCount(@Param("id") UUID id);
}
