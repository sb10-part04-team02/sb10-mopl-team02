package com.team02.mopl.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

class ContentRepositoryTest extends RepositoryTestSupport {

  @Autowired private ContentRepository contentRepository;

  @Autowired private ReviewRepository reviewRepository;

  @Autowired private EntityManager em;

  private UUID contentId;

  @BeforeEach
  void setUp() {
    contentId = insertContent();
  }

  @Test
  @DisplayName("refreshRatingAggregate는 활성 리뷰만으로 평균 평점·리뷰 수를 갱신하고 1을 반환한다")
  void refreshRatingAggregate_updatesAverageAndCountFromActiveReviews() {
    reviewRepository.save(new Review(insertUser(), contentId, "리뷰1", 4.0));
    reviewRepository.save(new Review(insertUser(), contentId, "리뷰2", 2.0));
    Review deleted = reviewRepository.save(new Review(insertUser(), contentId, "삭제될 리뷰", 5.0));
    em.flush();
    deleted.delete();
    em.flush();

    int updated = contentRepository.refreshRatingAggregate(contentId);
    // 벌크 UPDATE는 영속성 컨텍스트를 우회하므로 clear 후 다시 읽어 갱신값을 검증한다
    em.clear();

    assertThat(updated).isEqualTo(1);
    Content content = contentRepository.findByIdAndDeletedAtIsNull(contentId).orElseThrow();
    assertThat(content.getReviewCount()).isEqualTo(2);
    assertThat(content.getAverageRating()).isEqualTo(3.0);
  }

  @Test
  @DisplayName("refreshRatingAggregate는 활성 리뷰가 없으면 평균 평점 0.0, 리뷰 수 0으로 갱신한다")
  void refreshRatingAggregate_whenNoActiveReviews_resetsToZero() {
    int updated = contentRepository.refreshRatingAggregate(contentId);
    em.clear();

    assertThat(updated).isEqualTo(1);
    Content content = contentRepository.findByIdAndDeletedAtIsNull(contentId).orElseThrow();
    assertThat(content.getReviewCount()).isEqualTo(0);
    assertThat(content.getAverageRating()).isEqualTo(0.0);
  }

  @Test
  @DisplayName("refreshRatingAggregate는 존재하지 않는 콘텐츠면 0을 반환한다")
  void refreshRatingAggregate_whenContentNotFound_returnsZero() {
    int updated = contentRepository.refreshRatingAggregate(UUID.randomUUID());

    assertThat(updated).isEqualTo(0);
  }

  @Test
  @DisplayName("findBySourceAndExternalId는 source와 externalId가 일치하는 콘텐츠를 조회한다")
  void findBySourceAndExternalId_returnsMatchingContent() {
    // given
    Content external =
        Content.createExternal(
            ContentSource.TMDB, "12345", ContentType.MOVIE, "외부 영화", "설명", "http://img");
    em.persist(external);
    em.flush();

    // when
    Content found =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "12345").orElseThrow();

    // then
    assertThat(found.getId()).isEqualTo(external.getId());
    assertThat(found.getSource()).isEqualTo(ContentSource.TMDB);
    assertThat(found.getExternalId()).isEqualTo("12345");
  }

  @Test
  @DisplayName("findBySourceAndExternalId는 소프트 삭제된 콘텐츠도 조회한다")
  void findBySourceAndExternalId_includesSoftDeletedContent() {
    // given
    Content external =
        Content.createExternal(
            ContentSource.TMDB, "12345", ContentType.MOVIE, "외부 영화", "설명", "http://img");
    em.persist(external);
    em.flush();
    external.delete();
    em.flush();

    // when
    Content found =
        contentRepository.findBySourceAndExternalId(ContentSource.TMDB, "12345").orElseThrow();

    // then - 논리 삭제된 콘텐츠도 조회되어야 재수집 가능함
    assertThat(found.isDeleted()).isTrue();
  }

  @Test
  @DisplayName("동일한 (source, externalId) 쌍은 유니크 인덱스가 중복 저장을 차단한다")
  void save_whenDuplicateSourceAndExternalId_throwsDataIntegrityViolation() {
    // given
    contentRepository.saveAndFlush(
        Content.createExternal(
            ContentSource.TMDB, "12345", ContentType.MOVIE, "외부 영화", "설명", "http://img"));

    // when
    Content duplicate =
        Content.createExternal(
            ContentSource.TMDB, "12345", ContentType.MOVIE, "중복 영화", "설명", "http://img");

    // then
    assertThatThrownBy(() -> contentRepository.saveAndFlush(duplicate))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("수동 생성 콘텐츠(source/externalId 모두 NULL)는 여러 건 저장할 수 있다")
  void save_whenSourceAndExternalIdAreNull_allowsMultipleRows() {
    // given - setUp에서 이미 수동 생성 콘텐츠 1건이 저장돼 있다
    em.persist(new Content(ContentType.MOVIE, "수동 영화 2", "설명", "http://img"));
    em.flush();

    // when & then
    assertThat(contentRepository.count()).isEqualTo(2);
  }

  private UUID insertUser() {
    UUID id = UUID.randomUUID();
    em.createNativeQuery(
            "INSERT INTO users (id, updated_at, name, email, role) "
                + "VALUES (:id, now(), :name, :email, 'USER')")
        .setParameter("id", id)
        .setParameter("name", "리뷰어")
        .setParameter("email", "reviewer-" + id + "@test.com")
        .executeUpdate();
    return id;
  }

  private UUID insertContent() {
    Content content = new Content(ContentType.MOVIE, "테스트 영화", "설명", "http://img");
    em.persist(content);
    em.flush();
    return content.getId();
  }
}
