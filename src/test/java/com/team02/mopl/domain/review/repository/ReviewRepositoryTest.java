package com.team02.mopl.domain.review.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.review.entity.Review;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReviewRepositoryTest {

  @Autowired private ReviewRepository reviewRepository;

  @PersistenceContext private EntityManager em;

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
