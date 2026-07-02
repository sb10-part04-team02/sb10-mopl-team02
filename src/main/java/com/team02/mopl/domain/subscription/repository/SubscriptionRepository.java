package com.team02.mopl.domain.subscription.repository;

import com.team02.mopl.domain.subscription.entity.Subscription;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {

  boolean existsByUserIdAndPlaylist_IdAndDeletedAtIsNull(UUID userId, UUID playlistId);

  List<Subscription> findByPlaylist_IdAndDeletedAtIsNull(UUID playlistId);
}
