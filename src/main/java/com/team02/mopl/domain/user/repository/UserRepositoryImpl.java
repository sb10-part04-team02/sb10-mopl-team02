package com.team02.mopl.domain.user.repository;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.user.entity.QUser;
import com.team02.mopl.domain.user.entity.User;
import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;
import org.springframework.util.StringUtils;

public class UserRepositoryImpl implements UserRepositoryCustom {

  private static final QUser user = QUser.user;
  private final JPAQueryFactory queryFactory;

  public UserRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<User> findUsersByCursor(
      String emailLike,
      Role roleEqual,
      Boolean isLocked,
      String cursor,
      UUID idAfter,
      Integer limit,
      SortDirection sortDirection,
      UserSortBy sortBy) {

    boolean ascending = sortDirection == SortDirection.ASCENDING;
    boolean firstPage = cursor == null && idAfter == null;
    if (cursor == null ^ idAfter == null) { // XOR: 둘 중 하나만 있을 경우
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    return queryFactory
        .selectFrom(user)
        .where(
            user.deletedAt.isNull(),
            emailLikeEq(emailLike),
            roleEq(roleEqual),
            isLockedEq(isLocked),
            firstPage ? null : cursorPredicate(sortBy, ascending, cursor, idAfter))
        .orderBy(orderSpecifiers(sortBy, ascending))
        // 조회전용 영속성 스냅샷 생성 생략
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  private BooleanExpression emailLikeEq(String emailLike) {
    return StringUtils.hasText(emailLike) ? user.email.contains(emailLike) : null;
  }

  private BooleanExpression roleEq(Role roleEqual) {
    return roleEqual != null ? user.role.eq(roleEqual) : null;
  }

  private BooleanExpression isLockedEq(Boolean isLocked) {
    return isLocked != null ? user.isLocked.eq(isLocked) : null;
  }

  private BooleanExpression cursorPredicate(
      UserSortBy sortBy, boolean ascending, String cursor, UUID idAfter) {
    record CursorTuple(Expression<?> sortKey, Object cursorValue) {}

    CursorTuple tuple =
        switch (sortBy) {
          case NAME -> new CursorTuple(user.name, cursor);
          case EMAIL -> new CursorTuple(user.email, cursor);
          case CREATED_AT -> new CursorTuple(user.createdAt, parseInstantCursor(cursor));
          case IS_LOCKED -> new CursorTuple(user.isLocked, parseBooleanCursor(cursor));
          case ROLE -> new CursorTuple(user.role, parseRoleCursor(cursor).name());
        };

    String op = ascending ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + op + " ({2}, {3})",
        tuple.sortKey,
        user.id,
        Expressions.constant(tuple.cursorValue),
        Expressions.constant(idAfter));
  }

  private OrderSpecifier<?>[] orderSpecifiers(UserSortBy sortBy, boolean ascending) {
    List<OrderSpecifier<?>> orders = new ArrayList<>();
    Order order = ascending ? Order.ASC : Order.DESC;

    // 1차 정렬
    OrderSpecifier<?> targetOrder =
        switch (sortBy) {
          case NAME -> new OrderSpecifier<>(order, user.name);
          case EMAIL -> new OrderSpecifier<>(order, user.email);
          case CREATED_AT -> new OrderSpecifier<>(order, user.createdAt);
          case IS_LOCKED -> new OrderSpecifier<>(order, user.isLocked);
          case ROLE -> new OrderSpecifier<>(order, user.role);
        };

    orders.add(targetOrder);
    orders.add(new OrderSpecifier<>(order, user.id)); // 2차정렬은 id

    return orders.toArray(OrderSpecifier[]::new);
  }

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
    }
  }

  private Boolean parseBooleanCursor(String cursor) {
    if (!"true".equalsIgnoreCase(cursor) && !"false".equalsIgnoreCase(cursor)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
    return Boolean.parseBoolean(cursor);
  }

  private Role parseRoleCursor(String cursor) {
    try {
      return Role.valueOf(cursor.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST, e);
    }
  }
}
