package com.team02.mopl.domain.dm.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
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
      UUID userId,
      String keyword,
      SortDirection direction,
      Instant cursor,
      UUID idAfter,
      int limit) {
    boolean ascending = direction == SortDirection.ASCENDING;
    boolean firstPage = cursor == null && idAfter == null;

    return queryFactory
        .selectFrom(conversation)
        .join(member)
        .on(member.conversation.eq(conversation), member.user.id.eq(userId))
        .where(
            conversation.deletedAt.isNull(),
            counterpartNameContains(userId, keyword),
            firstPage ? null : cursorCondition(cursor, idAfter, ascending))
        .orderBy(
            ascending ? conversation.createdAt.asc() : conversation.createdAt.desc(),
            ascending ? conversation.id.asc() : conversation.id.desc())
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  @Override
  public long countByMemberUserId(UUID userId, String keyword) {
    Long count =
        queryFactory
            .select(conversation.count())
            .from(conversation)
            .join(member)
            .on(member.conversation.eq(conversation), member.user.id.eq(userId))
            .where(conversation.deletedAt.isNull(), counterpartNameContains(userId, keyword))
            .fetchOne();
    return count == null ? 0L : count;
  }

  // 상대방 이름 부분일치 (대소문자 구분 없음). keyword가 없으면 필터 미적용.
  // EXISTS 서브쿼리로 표현해 조인 중복 행을 만들지 않고 커서 정렬을 그대로 유지한다
  private BooleanExpression counterpartNameContains(UUID userId, String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    QConversationMember counterpart = new QConversationMember("counterpart");
    return JPAExpressions.selectOne()
        .from(counterpart)
        .where(
            counterpart.conversation.eq(conversation),
            counterpart.user.id.ne(userId),
            counterpart.user.name.containsIgnoreCase(keyword))
        .exists();
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
