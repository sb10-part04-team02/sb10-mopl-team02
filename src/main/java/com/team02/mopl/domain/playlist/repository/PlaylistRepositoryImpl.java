package com.team02.mopl.domain.playlist.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.entity.QPlaylist;
import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.domain.subscription.entity.QSubscription;
import com.team02.mopl.global.enums.SortDirection;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class PlaylistRepositoryImpl implements PlaylistRepositoryCustom {

  private static final QPlaylist playlist = QPlaylist.playlist;
  private static final QSubscription subscription = QSubscription.subscription;

  private final JPAQueryFactory queryFactory;

  public PlaylistRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<Playlist> findPlaylistsByCursor(
      String keyword,
      PlaylistSortBy sortBy,
      SortDirection direction,
      Comparable<?> cursor,
      UUID idAfter,
      int limit,
      UUID ownerIdEqual,
      UUID subscriberIdEqual) {
    boolean ascending = direction == SortDirection.ASCENDING;
    // cursor·idAfter는 항상 함께 와야 한다. 둘 다 없으면 첫 페이지로 동작한다
    boolean firstPage = cursor == null && idAfter == null;

    return queryFactory
        .selectFrom(playlist)
        .where(
            playlist.deletedAt.isNull(),
            keywordContains(keyword),
            ownerIdEquals(ownerIdEqual),
            subscriberIdEquals(subscriberIdEqual),
            firstPage ? null : cursorPredicate(sortBy, ascending, cursor, idAfter))
        .orderBy(orderSpecifiers(sortBy, ascending))
        // 조회 전용이므로 영속성 스냅샷 생성을 생략
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  @Override
  public long countActive(String keyword, UUID ownerIdEqual, UUID subscriberIdEqual) {
    Long count =
        queryFactory
            .select(playlist.count())
            .from(playlist)
            .where(
                playlist.deletedAt.isNull(),
                keywordContains(keyword),
                ownerIdEquals(ownerIdEqual),
                subscriberIdEquals(subscriberIdEqual))
            .fetchOne();
    return count == null ? 0L : count;
  }

  // 제목·설명 부분일치 (대소문자 구분 없음). keyword가 없으면 필터 미적용
  // TODO: containsIgnoreCase는 LIKE '%kw%' (선행 와일드카드)로 번역돼 B-Tree 인덱스를 못 타고 순차 스캔함.
  //  데이터가 많아지면 pg_trgm + GIN 표현식 인덱스(LOWER(title)/LOWER(description))로 부분 문자열 검색을
  //  가속하거나, 검색 요구가 커지면 ElasticSearch 도입을 검토.
  private BooleanExpression keywordContains(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return playlist
        .title
        .containsIgnoreCase(keyword)
        .or(playlist.description.containsIgnoreCase(keyword));
  }

  // 소유자 필터: ownerIdEqual이 없으면 미적용
  private BooleanExpression ownerIdEquals(UUID ownerIdEqual) {
    return ownerIdEqual == null ? null : playlist.ownerId.eq(ownerIdEqual);
  }

  // 구독자 필터: 해당 유저의 활성(삭제되지 않은) 구독이 존재하는 플레이리스트만. subscriberIdEqual이 없으면 미적용.
  // EXISTS 서브쿼리로 표현해 조인 중복 행을 만들지 않고 커서 정렬을 그대로 유지한다
  private BooleanExpression subscriberIdEquals(UUID subscriberIdEqual) {
    if (subscriberIdEqual == null) {
      return null;
    }
    return JPAExpressions.selectOne()
        .from(subscription)
        .where(
            subscription.playlist.id.eq(playlist.id),
            subscription.userId.eq(subscriberIdEqual),
            subscription.deletedAt.isNull())
        .exists();
  }

  // 복합키 (정렬값, id) 비교를 row-value 표현식으로 구성한다 (동률 다수 대응)
  private BooleanExpression cursorPredicate(
      PlaylistSortBy sortBy, boolean ascending, Comparable<?> cursor, UUID idAfter) {
    if (sortBy == PlaylistSortBy.SUBSCRIBE_COUNT) {
      return rowComparison(playlist.subscriberCount, ascending, (Long) cursor, idAfter);
    }
    return rowComparison(playlist.updatedAt, ascending, (Instant) cursor, idAfter);
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
        playlist.id,
        Expressions.constant(sortValue),
        Expressions.constant(idAfter));
  }

  private OrderSpecifier<?>[] orderSpecifiers(PlaylistSortBy sortBy, boolean ascending) {
    if (sortBy == PlaylistSortBy.SUBSCRIBE_COUNT) {
      return ascending
          ? new OrderSpecifier<?>[] {playlist.subscriberCount.asc(), playlist.id.asc()}
          : new OrderSpecifier<?>[] {playlist.subscriberCount.desc(), playlist.id.desc()};
    }
    return ascending
        ? new OrderSpecifier<?>[] {playlist.updatedAt.asc(), playlist.id.asc()}
        : new OrderSpecifier<?>[] {playlist.updatedAt.desc(), playlist.id.desc()};
  }
}
