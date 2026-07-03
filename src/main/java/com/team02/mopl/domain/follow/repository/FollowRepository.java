package com.team02.mopl.domain.follow.repository;

import com.team02.mopl.domain.follow.entity.Follow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FollowRepository extends JpaRepository<Follow, UUID> {

  // 중복 방지
  boolean existsByFollower_IdAndFollowee_IdAndDeletedAtIsNull(UUID followerId, UUID followeeId);

  // 단건 조회
  Optional<Follow> findByIdAndDeletedAtIsNull(UUID id);

  // 본인이 특정 사용자 팔로우 중인지 조회
  Optional<Follow> findByFollower_IdAndFollowee_IdAndDeletedAtIsNull(
      UUID followerId, UUID followeeId);

  // 특정 사용자의 팔로워 수 조회
  long countByFollowee_IdAndDeletedAtIsNull(UUID followeeId);

  // 특정 사용자를 팔로우 중인 활성 팔로우 목록 조회
  List<Follow> findByFollowee_IdAndDeletedAtIsNull(UUID followeeId);

  @Query(
      """
      select f.follower.id
      from Follow f
      where f.followee.id = :followeeId
        and f.deletedAt is null
        and f.follower.deletedAt is null
      """)
  List<UUID> findActiveFollowerIdsByFolloweeId(@Param("followeeId") UUID followeeId);
}
