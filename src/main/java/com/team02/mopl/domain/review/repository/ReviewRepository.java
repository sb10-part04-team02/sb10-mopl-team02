package com.team02.mopl.domain.review.repository;

import com.team02.mopl.domain.review.entity.Review;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

  boolean existsByAuthorIdAndContentIdAndDeletedAtIsNull(UUID authorId, UUID contentId);

  Optional<Review> findByIdAndDeletedAtIsNull(UUID id);

  // 콘텐츠 필터(nullable)를 적용한 활성 리뷰 총 개수
  @Query(
      "SELECT COUNT(r) FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId)")
  long countActive(@Param("contentId") UUID contentId);
}
