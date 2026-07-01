package com.team02.mopl.domain.content.repository;

import com.team02.mopl.domain.content.entity.Content;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentRepository extends JpaRepository<Content, UUID>, ContentRepositoryCustom {

  // 활성(논리 삭제되지 않은) 콘텐츠 단건 조회
  // SELECT c.* FROM contents c WHERE c.id = ? AND c.deleted_at IS NULL;
  Optional<Content> findByIdAndDeletedAtIsNull(UUID id);

  // 콘텐츠의 활성 리뷰를 전량 재집계해 평균 평점·리뷰 수를 단일 UPDATE로 갱신
  // 행 잠금 하에 즉시 재집계하므로 read-modify-write 사이의 동시성 틈이 없고, DB 왕복도 1회로 줄인다
  @Modifying
  @Query(
      "UPDATE Content c SET "
          + "c.averageRating = COALESCE("
          + "(SELECT AVG(r.rating) FROM Review r "
          + "WHERE r.contentId = c.id AND r.deletedAt IS NULL), 0.0), "
          + "c.reviewCount = "
          + "(SELECT COUNT(r) FROM Review r "
          + "WHERE r.contentId = c.id AND r.deletedAt IS NULL) "
          + "WHERE c.id = :contentId AND c.deletedAt IS NULL")
  int refreshRatingAggregate(@Param("contentId") UUID contentId);
}
