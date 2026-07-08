package com.team02.mopl.domain.content.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team02.mopl.domain.content.dto.ContentSearchCondition;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.repository.ReviewRepository;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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
  @DisplayName("search와 countBySearch는 논리 삭제된 콘텐츠를 제외한다")
  void search_excludesSoftDeletedContent() {
    // given
    insertDeletedContent();
    ContentSearchCondition condition =
        new ContentSearchCondition(null, null, List.of(), SortBy.CREATED_AT, true, null, null, 10);

    // when
    List<Content> results = contentRepository.search(condition);
    long totalCount = contentRepository.countBySearch(condition);

    // then
    assertThat(results).extracting(Content::getId).containsExactly(contentId);
    assertThat(totalCount).isEqualTo(1);
  }

  @Test
  @DisplayName("findByIdAndDeletedAtIsNull은 논리 삭제된 콘텐츠를 제외한다")
  void findByIdAndDeletedAtIsNull_excludesSoftDeletedContent() {
    // given
    UUID deletedContentId = insertDeletedContent();

    // when & then
    assertThat(contentRepository.findByIdAndDeletedAtIsNull(deletedContentId)).isEmpty();
    assertThat(contentRepository.findByIdAndDeletedAtIsNull(contentId)).isPresent();
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

  private UUID insertDeletedContent() {
    Content content = new Content(ContentType.MOVIE, "삭제된 영화", "설명", "http://img");
    em.persist(content);
    em.flush();
    content.delete();
    em.flush();
    return content.getId();
  }
}
