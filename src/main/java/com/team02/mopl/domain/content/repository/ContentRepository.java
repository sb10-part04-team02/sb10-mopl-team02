package com.team02.mopl.domain.content.repository;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentSource;
import java.util.List;
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

  // 여러 활성 콘텐츠 일괄 조회 (플레이리스트 포함 콘텐츠 N+1 방지)
  // SELECT c.* FROM contents c WHERE c.id IN (?, ?, ...) AND c.deleted_at IS NULL;
  List<Content> findByIdInAndDeletedAtIsNull(List<UUID> ids);

  // 외부 수집 중복 방지 키 조회. 삭제 행 재수집 정책 분기를 위해 deleted_at 필터를 걸지 않는다
  // SELECT c.* FROM contents c WHERE c.source = ? AND c.external_id = ?;
  Optional<Content> findBySourceAndExternalId(ContentSource source, String externalId);

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
