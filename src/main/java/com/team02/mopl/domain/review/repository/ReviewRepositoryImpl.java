package com.team02.mopl.domain.review.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.review.entity.QReview;
import com.team02.mopl.domain.review.entity.Review;
import com.team02.mopl.domain.review.enums.ReviewSortBy;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class ReviewRepositoryImpl implements ReviewRepositoryCustom {

  private static final QReview review = QReview.review;

  private final JPAQueryFactory queryFactory;

  public ReviewRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<Review> findReviewsByCursor(
      UUID contentId,
      ReviewSortBy sortBy,
      SortDirection direction,
      String cursor,
      UUID idAfter,
      int limit) {
    boolean ascending = direction == SortDirection.ASCENDING;
    boolean firstPage = cursor == null || idAfter == null;

    return queryFactory
        .selectFrom(review)
        .where(
            review.deletedAt.isNull(),
            contentEq(contentId),
            firstPage ? null : cursorPredicate(sortBy, ascending, cursor, idAfter))
        .orderBy(orderSpecifiers(sortBy, ascending))
        // 조회 전용이므로 영속성 스냅샷 생성을 생략
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  private BooleanExpression contentEq(UUID contentId) {
    return contentId == null ? null : review.contentId.eq(contentId);
  }

  // 복합키 (정렬값, id) 비교를 row-value 표현식으로 구성한다 (동률 다수 대응)
  private BooleanExpression cursorPredicate(
      ReviewSortBy sortBy, boolean ascending, String cursor, UUID idAfter) {
    if (sortBy == ReviewSortBy.RATING) {
      double rating = parseDoubleCursor(cursor);
      return rowComparison(review.rating, ascending, rating, idAfter);
    }
    Instant createdAt = parseInstantCursor(cursor);
    return rowComparison(review.createdAt, ascending, createdAt, idAfter);
  }

  // (정렬키, id) row-value 비교: ASC면 >, DESC면 < 로 커서 이후 항목만 조회
  private BooleanExpression rowComparison(
      com.querydsl.core.types.Expression<?> sortKey,
      boolean ascending,
      Object sortValue,
      UUID idAfter) {
    String op = ascending ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + op + " ({2}, {3})",
        sortKey,
        review.id,
        Expressions.constant(sortValue),
        Expressions.constant(idAfter));
  }

  private OrderSpecifier<?>[] orderSpecifiers(ReviewSortBy sortBy, boolean ascending) {
    if (sortBy == ReviewSortBy.RATING) {
      return ascending
          ? new OrderSpecifier<?>[] {review.rating.asc(), review.id.asc()}
          : new OrderSpecifier<?>[] {review.rating.desc(), review.id.desc()};
    }
    return ascending
        ? new OrderSpecifier<?>[] {review.createdAt.asc(), review.id.asc()}
        : new OrderSpecifier<?>[] {review.createdAt.desc(), review.id.desc()};
  }

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }

  private double parseDoubleCursor(String cursor) {
    try {
      return Double.parseDouble(cursor);
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }
}
