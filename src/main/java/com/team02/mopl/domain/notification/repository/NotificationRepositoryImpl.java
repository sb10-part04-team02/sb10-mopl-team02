package com.team02.mopl.domain.notification.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.notification.entity.Notification;
import com.team02.mopl.domain.notification.entity.QNotification;
import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class NotificationRepositoryImpl implements NotificationRepositoryCustom {

  private static final QNotification notification = QNotification.notification;

  private final JPAQueryFactory queryFactory;

  public NotificationRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<Notification> findNotificationsByCursor(
      UUID receiverId,
      NotificationSortBy sortBy,
      SortDirection direction,
      String cursor,
      UUID idAfter,
      int limit) {
    // ascending이면 true
    boolean ascending = direction == SortDirection.ASCENDING;
    // 첫 페이지 기준 둘다 null
    boolean firstPage = cursor == null && idAfter == null;

    if (!firstPage && (cursor == null || idAfter == null)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    return queryFactory
        .selectFrom(notification)
        .where(
            notification.receiver.id.eq(receiverId),
            notification.deletedAt.isNull(),
            firstPage ? null : cursorCondition(cursor, idAfter, ascending))
        .orderBy(
            ascending ? notification.createdAt.asc() : notification.createdAt.desc(),
            ascending ? notification.id.asc() : notification.id.desc())

        // 조회 전용 쿼리라는 힌트
        // Hibernate가 변경 감지를 위한 부담 감소
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  private BooleanExpression cursorCondition(String cursor, UUID idAfter, boolean ascending) {
    Instant createdAt = parseInstantCursor(cursor);
    String operator = ascending ? ">" : "<";

    return Expressions.booleanTemplate(
        "({0}, {1}) " + operator + " ({2}, {3})",
        notification.createdAt,
        notification.id,
        Expressions.constant(createdAt),
        Expressions.constant(idAfter));
  }

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }
}
