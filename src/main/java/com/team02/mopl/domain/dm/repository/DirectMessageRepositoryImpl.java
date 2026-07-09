package com.team02.mopl.domain.dm.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.dm.entity.DirectMessage;
import com.team02.mopl.domain.dm.entity.QConversationMember;
import com.team02.mopl.domain.dm.entity.QDirectMessage;
import com.team02.mopl.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class DirectMessageRepositoryImpl implements DirectMessageRepositoryCustom {

  private static final QDirectMessage dm = QDirectMessage.directMessage;
  private static final QConversationMember sender = new QConversationMember("sender");
  private static final QConversationMember receiver = new QConversationMember("receiver");

  private final JPAQueryFactory queryFactory;

  public DirectMessageRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<DirectMessage> findDirectMessagesByCursor(
      UUID conversationId, SortDirection direction, Instant cursor, UUID idAfter, int limit) {
    boolean ascending = direction == SortDirection.ASCENDING;
    boolean firstPage = cursor == null && idAfter == null;

    return queryFactory
        .selectFrom(dm)
        .join(dm.sender, sender)
        .fetchJoin()
        .join(sender.user)
        .fetchJoin()
        .join(dm.receiver, receiver)
        .fetchJoin()
        .join(receiver.user)
        .fetchJoin()
        .where(
            dm.conversation.id.eq(conversationId),
            firstPage ? null : cursorPredicate(ascending, cursor, idAfter))
        .orderBy(orderSpecifiers(ascending))
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  private BooleanExpression cursorPredicate(boolean ascending, Instant cursor, UUID idAfter) {
    String op = ascending ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + op + " ({2}, {3})",
        dm.createdAt,
        dm.id,
        Expressions.constant(cursor),
        Expressions.constant(idAfter));
  }

  private OrderSpecifier<?>[] orderSpecifiers(boolean ascending) {
    return ascending
        ? new OrderSpecifier<?>[] {dm.createdAt.asc(), dm.id.asc()}
        : new OrderSpecifier<?>[] {dm.createdAt.desc(), dm.id.desc()};
  }
}
