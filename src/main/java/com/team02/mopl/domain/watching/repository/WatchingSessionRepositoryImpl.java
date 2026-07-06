package com.team02.mopl.domain.watching.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.user.entity.QUser;
import com.team02.mopl.domain.watching.entity.QWatchingSession;
import com.team02.mopl.domain.watching.entity.WatchingSession;
import com.team02.mopl.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class WatchingSessionRepositoryImpl implements WatchingSessionRepositoryCustom {

  private static final QWatchingSession session = QWatchingSession.watchingSession;
  private static final QUser user = QUser.user;

  private final JPAQueryFactory queryFactory;

  public WatchingSessionRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  // 특정 콘텐츠에 대한 활성 시청 세션을 커서 기반으로 페이지네이션 조회
  // cursor는 이미 검증/변환이 끝난 Instant 값(첫 페이지면 null)으로 전달받는다
  @Override
  public List<WatchingSession> findActiveSessionsByCursor(
      UUID contentId,
      String watcherNameLike,
      SortDirection direction,
      Instant cursor,
      UUID idAfter,
      int limit) {
    boolean ascending = direction == SortDirection.ASCENDING;
    boolean firstPage = cursor == null;

    return queryFactory
        .selectFrom(session)
        .join(session.user, user)
        .fetchJoin()
        .where(
            activeSessionOfContent(contentId), // 콘텐츠 기준 활성 세션
            watcherNameContains(watcherNameLike), // 시청자 이름 부분 검색
            firstPage ? null : cursorCondition(cursor, idAfter, ascending))
        .orderBy(
            ascending ? session.createdAt.asc() : session.createdAt.desc(),
            ascending ? session.id.asc() : session.id.desc())
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  // 특정 콘텐츠의 활성 시청 세션 총 개수를 조회
  @Override
  public long countActiveSessions(UUID contentId, String watcherNameLike) {
    Long count =
        queryFactory
            .select(session.count())
            .from(session)
            .where(activeSessionOfContent(contentId), watcherNameContains(watcherNameLike))
            .fetchOne();
    return count == null ? 0L : count; // NPE 방지
  }

  // 특정 콘텐츠의 활성(미종료 + 논리삭제 제외) 세션 조건
  private BooleanExpression activeSessionOfContent(UUID contentId) {
    return session
        .content
        .id
        .eq(contentId)
        .and(session.exitedAt.isNull())
        .and(session.deletedAt.isNull());
  }

  // 시청자 이름 부분 매칭 조건 - 대소문자 구분 없음
  private BooleanExpression watcherNameContains(String watcherNameLike) {
    if (watcherNameLike == null || watcherNameLike.isBlank()) {
      return null;
    }
    return session.user.name.containsIgnoreCase(watcherNameLike);
  }

  private BooleanExpression cursorCondition(Instant cursor, UUID idAfter, boolean ascending) {
    String op = ascending ? ">" : "<";
    return Expressions.booleanTemplate(
        "({0}, {1}) " + op + " ({2}, {3})",
        session.createdAt,
        session.id,
        Expressions.constant(cursor),
        Expressions.constant(idAfter));
  }
}
