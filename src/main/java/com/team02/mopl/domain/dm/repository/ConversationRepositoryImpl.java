package com.team02.mopl.domain.dm.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.dm.entity.Conversation;
import com.team02.mopl.domain.dm.entity.QConversation;
import com.team02.mopl.domain.dm.entity.QConversationMember;
import com.team02.mopl.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class ConversationRepositoryImpl implements ConversationRepositoryCustom {

  private static final QConversation conversation = QConversation.conversation;
  private static final QConversationMember member = QConversationMember.conversationMember;

  private final JPAQueryFactory queryFactory;

  public ConversationRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<Conversation> findConversationsByCursor(
      UUID userId, SortDirection direction, Instant cursor, UUID idAfter, int limit) {
    boolean ascending = direction == SortDirection.ASCENDING;
    boolean firstPage = cursor == null && idAfter == null;

    return queryFactory
        .selectFrom(conversation)
        .join(member)
        .on(member.conversation.eq(conversation), member.user.id.eq(userId))
        .where(
            conversation.deletedAt.isNull(),
            firstPage ? null : cursorCondition(cursor, idAfter, ascending))
        .orderBy(
            ascending ? conversation.createdAt.asc() : conversation.createdAt.desc(),
            ascending ? conversation.id.asc() : conversation.id.desc())
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  private BooleanExpression cursorCondition(Instant cursor, UUID idAfter, boolean ascending) {
    String op = ascending ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + op + " ({2}, {3})",
        conversation.createdAt,
        conversation.id,
        Expressions.constant(cursor),
        Expressions.constant(idAfter));
  }
}
