package com.team02.mopl.domain.review.repository;

import com.team02.mopl.domain.review.dto.ReviewAggregate;
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

  // 콘텐츠의 활성(논리 삭제 제외) 리뷰 수·평균 평점 집계
  // 리뷰가 없으면 count=0, averageRating=null로 반환되므로 호출부에서 0.0 처리
  @Query(
      "SELECT new com.team02.mopl.domain.review.dto.ReviewAggregate("
          + "COUNT(r), AVG(r.rating)) "
          + "FROM Review r "
          + "WHERE r.contentId = :contentId AND r.deletedAt IS NULL")
  ReviewAggregate aggregateByContentId(@Param("contentId") UUID contentId);
}
