package com.team02.mopl.domain.review.repository;

import com.team02.mopl.domain.review.entity.Review;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

  boolean existsByAuthorIdAndContentIdAndDeletedAtIsNull(UUID authorId, UUID contentId);

  Optional<Review> findByIdAndDeletedAtIsNull(UUID id);

  // 콘텐츠 필터(nullable)를 적용한 활성 리뷰 총 개수
  @Query(
      "SELECT COUNT(r) FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId)")
  long countActive(@Param("contentId") UUID contentId);

  // === createdAt 정렬 (복합키 (createdAt, id) 비교) ===
  // 조회 전용이므로 readOnly 힌트로 영속성 스냅샷 생성을 생략한다
  // 첫 페이지: 커서 없이 정렬만 적용
  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "ORDER BY r.createdAt DESC, r.id DESC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findFirstByCreatedAtDesc(@Param("contentId") UUID contentId, Pageable pageable);

  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "ORDER BY r.createdAt ASC, r.id ASC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findFirstByCreatedAtAsc(@Param("contentId") UUID contentId, Pageable pageable);

  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "AND (r.createdAt, r.id) < (:cursor, :idAfter) "
          + "ORDER BY r.createdAt DESC, r.id DESC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findNextByCreatedAtDesc(
      @Param("contentId") UUID contentId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable);

  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "AND (r.createdAt, r.id) > (:cursor, :idAfter) "
          + "ORDER BY r.createdAt ASC, r.id ASC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findNextByCreatedAtAsc(
      @Param("contentId") UUID contentId,
      @Param("cursor") Instant cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable);

  // === rating 정렬 (복합키 (rating, id) 비교, 동률 다수 대응) ===
  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "ORDER BY r.rating DESC, r.id DESC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findFirstByRatingDesc(@Param("contentId") UUID contentId, Pageable pageable);

  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "ORDER BY r.rating ASC, r.id ASC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findFirstByRatingAsc(@Param("contentId") UUID contentId, Pageable pageable);

  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "AND (r.rating, r.id) < (:cursor, :idAfter) "
          + "ORDER BY r.rating DESC, r.id DESC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findNextByRatingDesc(
      @Param("contentId") UUID contentId,
      @Param("cursor") double cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable);

  @Query(
      "SELECT r FROM Review r "
          + "WHERE r.deletedAt IS NULL AND (:contentId IS NULL OR r.contentId = :contentId) "
          + "AND (r.rating, r.id) > (:cursor, :idAfter) "
          + "ORDER BY r.rating ASC, r.id ASC")
  @QueryHints(@QueryHint(name = "org.hibernate.readOnly", value = "true"))
  List<Review> findNextByRatingAsc(
      @Param("contentId") UUID contentId,
      @Param("cursor") double cursor,
      @Param("idAfter") UUID idAfter,
      Pageable pageable);
}
