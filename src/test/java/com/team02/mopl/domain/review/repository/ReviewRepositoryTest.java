package com.team02.mopl.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import com.team02.mopl.support.RepositoryTestSupport;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

class ReviewRepositoryTest extends RepositoryTestSupport {

  @Autowired private ReviewRepository reviewRepository;

  @Autowired private EntityManager em;

  private UUID authorId;
  private UUID contentId;

  @BeforeEach
  void setUp() {
    authorId = insertUser();
    contentId = insertContent();
  }

  @Test
  @DisplayName("활성 리뷰가 존재하면 existsByAuthorIdAndContentIdAndDeletedAtIsNull이 true를 반환한다")
  void existsActiveReview_whenReviewExists_returnsTrue() {
    reviewRepository.save(new Review(authorId, contentId, "재밌어요", 4.5));
    em.flush();

    boolean exists =
        reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(authorId, contentId);

    assertThat(exists).isTrue();
  }

  @Test
  @DisplayName("리뷰가 존재하지 않으면 existsByAuthorIdAndContentIdAndDeletedAtIsNull이 false를 반환한다")
  void existsActiveReview_whenReviewDoesNotExist_returnsFalse() {
    boolean exists =
        reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(authorId, contentId);

    assertThat(exists).isFalse();
  }

  @Test
  @DisplayName("소프트 삭제된 리뷰는 활성 중복 검사에서 제외되어 false를 반환한다")
  void existsActiveReview_whenReviewSoftDeleted_returnsFalse() {
    Review review = reviewRepository.save(new Review(authorId, contentId, "삭제될 리뷰", 3.0));
    em.flush();

    review.delete();
    em.flush();

    boolean exists =
        reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(authorId, contentId);

    assertThat(exists).isFalse();
  }

  @Test
  @DisplayName("활성 상태에서 같은 (author_id, content_id)로 리뷰를 두 번 저장하면 부분 유니크 인덱스 위반 예외가 발생한다")
  void save_duplicateActiveAuthorAndContent_violatesUniqueConstraint() {
    reviewRepository.save(new Review(authorId, contentId, "첫 리뷰", 4.0));
    em.flush();

    reviewRepository.save(new Review(authorId, contentId, "중복 리뷰", 5.0));

    assertThatThrownBy(() -> em.flush()).isInstanceOf(ConstraintViolationException.class);
  }

  @Test
  @DisplayName("기존 리뷰를 소프트 삭제하면 같은 (author_id, content_id)로 새 리뷰를 작성할 수 있다")
  void save_afterSoftDelete_allowsReinsertWithSameKey() {
    Review first = reviewRepository.save(new Review(authorId, contentId, "첫 리뷰", 4.0));
    em.flush();

    first.delete();
    em.flush();

    reviewRepository.save(new Review(authorId, contentId, "재작성 리뷰", 5.0));

    em.flush();

    assertThat(reviewRepository.existsByAuthorIdAndContentIdAndDeletedAtIsNull(authorId, contentId))
        .isTrue();
  }

  @Test
  @DisplayName("countActive는 콘텐츠 필터를 적용해 활성 리뷰 수만 센다")
  void countActive_filtersByContentAndExcludesDeleted() {
    reviewRepository.save(new Review(authorId, contentId, "리뷰1", 4.0));
    Review deleted = reviewRepository.save(new Review(insertUser(), contentId, "삭제", 3.0));
    em.flush();
    deleted.delete();
    em.flush();

    assertThat(reviewRepository.countActive(contentId)).isEqualTo(1L);
    assertThat(reviewRepository.countActive(UUID.randomUUID())).isEqualTo(0L);
  }

  @Test
  @DisplayName("findReviewsByCursor는 (rating, id) 복합키 비교로 커서 이후 항목만 평점 내림차순 조회한다")
  void findReviewsByCursor_ratingDesc_returnsItemsAfterCursorByCompositeKey() {
    Review high = reviewRepository.save(new Review(insertUser(), contentId, "5점", 5.0));
    Review mid = reviewRepository.save(new Review(insertUser(), contentId, "4점", 4.0));
    Review low = reviewRepository.save(new Review(insertUser(), contentId, "3점", 3.0));
    em.flush();

    List<Review> next =
        reviewRepository.findReviewsByCursor(
            contentId,
            ReviewSortBy.RATING,
            SortDirection.DESCENDING,
            Double.toString(high.getRating()),
            high.getId(),
            10);

    assertThat(next).extracting(Review::getId).containsExactly(mid.getId(), low.getId());
  }

  @Test
  @DisplayName("findReviewsByCursor는 동점 평점에서 (rating, id) 비교로 같은 평점 커서 이후 항목만 조회한다")
  void findReviewsByCursor_ratingDesc_tieBreaksById() {
    // 같은 rating 두 건 + 더 낮은 rating 한 건을 만들어 (rating, id) 타이브레이커를 검증한다
    reviewRepository.save(new Review(insertUser(), contentId, "동점A", 4.0));
    reviewRepository.save(new Review(insertUser(), contentId, "동점B", 4.0));
    reviewRepository.save(new Review(insertUser(), contentId, "3점", 3.0));
    em.flush();

    // DB 정렬 순서(uuid 비교는 PostgreSQL 기준)를 신뢰하기 위해 첫 페이지로 실제 순서를 확보한다
    List<Review> firstPage =
        reviewRepository.findReviewsByCursor(
            contentId, ReviewSortBy.RATING, SortDirection.DESCENDING, null, null, 10);
    assertThat(firstPage).hasSize(3);

    // 첫 동점 항목을 커서로 두면 나머지 동점 1건 + 낮은 평점 1건이 이어서 조회된다
    Review cursor = firstPage.get(0);
    Review afterTie = firstPage.get(1);
    Review low = firstPage.get(2);

    List<Review> next =
        reviewRepository.findReviewsByCursor(
            contentId,
            ReviewSortBy.RATING,
            SortDirection.DESCENDING,
            Double.toString(cursor.getRating()),
            cursor.getId(),
            10);

    assertThat(next).extracting(Review::getId).containsExactly(afterTie.getId(), low.getId());
  }

  @Test
  @DisplayName("findReviewsByCursor는 커서가 없으면 (createdAt, id) 내림차순 첫 페이지를 조회한다")
  void findReviewsByCursor_firstPage_returnsByCreatedAtDesc() {
    Review first = reviewRepository.save(new Review(insertUser(), contentId, "리뷰1", 4.0));
    Review second = reviewRepository.save(new Review(insertUser(), contentId, "리뷰2", 5.0));
    em.flush();

    List<Review> page =
        reviewRepository.findReviewsByCursor(
            contentId, ReviewSortBy.CREATED_AT, SortDirection.DESCENDING, null, null, 10);

    assertThat(page).extracting(Review::getId).containsExactly(second.getId(), first.getId());
  }

  @Test
  @DisplayName("findReviewsByCursor는 rating 정렬에서 cursor가 숫자가 아니면 INVALID_REQUEST 예외를 던진다")
  void findReviewsByCursor_invalidRatingCursor_throwsInvalidRequest() {
    UUID idAfter = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                reviewRepository.findReviewsByCursor(
                    contentId,
                    ReviewSortBy.RATING,
                    SortDirection.DESCENDING,
                    "not-a-number",
                    idAfter,
                    10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @ParameterizedTest
  @ValueSource(strings = {"NaN", "Infinity", "-Infinity"})
  @DisplayName("findReviewsByCursor는 rating 커서가 NaN·Infinity면 INVALID_REQUEST 예외를 던진다")
  void findReviewsByCursor_nonFiniteRatingCursor_throwsInvalidRequest(String cursor) {
    UUID idAfter = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                reviewRepository.findReviewsByCursor(
                    contentId, ReviewSortBy.RATING, SortDirection.DESCENDING, cursor, idAfter, 10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
  }

  @Test
  @DisplayName("findReviewsByCursor는 cursor·idAfter 중 하나만 전달되면 INVALID_REQUEST 예외를 던진다")
  void findReviewsByCursor_partialCursor_throwsInvalidRequest() {
    assertThatThrownBy(
            () ->
                reviewRepository.findReviewsByCursor(
                    contentId,
                    ReviewSortBy.CREATED_AT,
                    SortDirection.DESCENDING,
                    null,
                    UUID.randomUUID(),
                    10))
        .isInstanceOf(BusinessException.class)
        .extracting(e -> ((BusinessException) e).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_REQUEST);
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
