package com.team02.mopl.domain.playlist.repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.playlist.entity.Playlist;
import com.team02.mopl.domain.playlist.entity.QPlaylist;
import com.team02.mopl.domain.playlist.enums.PlaylistSortBy;
import com.team02.mopl.global.enums.SortDirection;
import com.team02.mopl.global.exception.BusinessException;
import com.team02.mopl.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;
import org.hibernate.jpa.HibernateHints;

public class PlaylistRepositoryImpl implements PlaylistRepositoryCustom {

  private static final QPlaylist playlist = QPlaylist.playlist;

  private final JPAQueryFactory queryFactory;

  public PlaylistRepositoryImpl(EntityManager em) {
    this.queryFactory = new JPAQueryFactory(em);
  }

  @Override
  public List<Playlist> findPlaylistsByCursor(
      String keyword,
      PlaylistSortBy sortBy,
      SortDirection direction,
      String cursor,
      UUID idAfter,
      int limit) {
    boolean ascending = direction == SortDirection.ASCENDING;
    // cursor·idAfter는 항상 함께 와야 한다. 둘 다 없으면 첫 페이지, 하나만 있으면 잘못된 요청
    boolean firstPage = cursor == null && idAfter == null;
    if (!firstPage && (cursor == null || idAfter == null)) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }

    return queryFactory
        .selectFrom(playlist)
        .where(
            playlist.deletedAt.isNull(),
            keywordContains(keyword),
            firstPage ? null : cursorPredicate(sortBy, ascending, cursor, idAfter))
        .orderBy(orderSpecifiers(sortBy, ascending))
        // 조회 전용이므로 영속성 스냅샷 생성을 생략
        .setHint(HibernateHints.HINT_READ_ONLY, true)
        .limit(limit)
        .fetch();
  }

  @Override
  public long countActive(String keyword) {
    Long count =
        queryFactory
            .select(playlist.count())
            .from(playlist)
            .where(playlist.deletedAt.isNull(), keywordContains(keyword))
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

  // 복합키 (정렬값, id) 비교를 row-value 표현식으로 구성한다 (동률 다수 대응)
  private BooleanExpression cursorPredicate(
      PlaylistSortBy sortBy, boolean ascending, String cursor, UUID idAfter) {
    if (sortBy == PlaylistSortBy.SUBSCRIBE_COUNT) {
      long subscriberCount = parseLongCursor(cursor);
      return rowComparison(playlist.subscriberCount, ascending, subscriberCount, idAfter);
    }
    Instant updatedAt = parseInstantCursor(cursor);
    return rowComparison(playlist.updatedAt, ascending, updatedAt, idAfter);
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

  private Instant parseInstantCursor(String cursor) {
    try {
      return Instant.parse(cursor);
    } catch (DateTimeParseException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }

  private long parseLongCursor(String cursor) {
    try {
      return Long.parseLong(cursor);
    } catch (NumberFormatException e) {
      throw new BusinessException(ErrorCode.INVALID_REQUEST);
    }
  }
}
