package com.team02.mopl.domain.content.repository;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparableExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team02.mopl.domain.content.dto.ContentSearchCondition;
import com.team02.mopl.domain.content.entity.Content;
import com.team02.mopl.domain.content.entity.QContent;
import com.team02.mopl.domain.content.entity.QTag;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.domain.watching.entity.QWatchingSession;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ContentRepositoryImpl implements ContentRepositoryCustom {

  private static final QContent content = QContent.content;
  private static final QTag tag = QTag.tag;
  private static final QWatchingSession watchingSession = QWatchingSession.watchingSession;

  private final JPAQueryFactory queryFactory;

  @Override
  public List<Content> search(ContentSearchCondition condition) {
    return queryFactory
        .selectFrom(content)
        .where(
            content.deletedAt.isNull(), // 논리 삭제되지 않은 컨텐츠만
            contentTypeEq(condition.type()), // 콘텐츠 타입 일치
            keywordContains(condition.keyword()), // 제목/설명 키워드 매칭
            tagsExists(condition.tags()), // 특정 태그 보유 여부
            cursorAfter(condition)) // 커서 페이지네이션 경계 조건
        .orderBy(cursorOrder(condition)) // 정렬 키와 id를 같은 방향으로 정렬
        .limit(condition.limit()) // 한 페이지에서 가져올 최대 행 수 (페이지 크기)
        .fetch();
  }

  // totalCount 산출용 - search()와 동일한 필터 적용하지만, 전체 건수만 집계
  @Override
  public long countBySearch(ContentSearchCondition condition) {
    Long count =
        queryFactory
            .select(content.count())
            .from(content)
            .where(
                content.deletedAt.isNull(),
                contentTypeEq(condition.type()),
                keywordContains(condition.keyword()),
                tagsExists(condition.tags()))
            .fetchOne();
    return count == null ? 0L : count; // NPE 방지
  }

  /// === 동적 조건 메서드 - null이면 where에서 무시됨 ===

  // 콘텐츠 타입 일치 조건
  private BooleanExpression contentTypeEq(ContentType type) {
    return type == null ? null : content.contentType.eq(type);
  }

  // 키워드 부분 매칭 조건 - 제목/설명 대소문자 구분 없이 부분 매칭
  // TODO: containsIgnoreCase는 LIKE '%kw%' (선행 와일드카드)로 번역돼 B-Tree 인덱스를 못 타고 순차 스캔함.
  //  데이터가 많아지면 pg_trgm + GIN 표현식 인덱스(LOWER(title)/LOWER(description))로 부분 문자열 검색을
  //  가속하거나, 검색 요구가 커지면 ElasticSearch 도입을 검토.
  private BooleanExpression keywordContains(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return null;
    }
    return content
        .title
        .containsIgnoreCase(keyword)
        .or(content.description.containsIgnoreCase(keyword));
  }

  // 태그 보유 조건 - 삭제되지 않은 태그 중 tags 목록에 있는 것을 하나라도 가진 콘텐츠
  private BooleanExpression tagsExists(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return null;
    }
    return JPAExpressions.selectOne()
        .from(tag)
        .where(tag.content.eq(content), tag.deletedAt.isNull(), tag.name.in(tags))
        .exists();
  }

  /// === 복합키 (sortKey, id) 커서 페이지네이션 동적 조건 메서드 ===

  // 커서 이후 데이터를 걸러내는 경계 조건
  private BooleanExpression cursorAfter(ContentSearchCondition condition) {
    Comparable<?> cursor = condition.cursor();
    if (cursor == null) {
      return null;
    }
    boolean asc = condition.asc();
    UUID idAfter = condition.idAfter();
    return switch (condition.sortBy()) {
      case CREATED_AT -> after(content.createdAt, (Instant) cursor, idAfter, asc);
      case RATE ->
          after(Expressions.asComparable(content.averageRating), (Double) cursor, idAfter, asc);
      case WATCHER_COUNT ->
          after(Expressions.asComparable(watcherCount()), (Long) cursor, idAfter, asc);
    };
  }

  private static <T extends Comparable<T>> BooleanExpression after(
      ComparableExpression<T> sortKey, // DB 컬럼 표현식 최신순, 평점순, 인기순 받음
      T cursor,
      UUID idAfter,
      boolean asc) {
    BooleanExpression keyStep = asc ? sortKey.gt(cursor) : sortKey.lt(cursor);
    BooleanExpression idCondition;
    if (asc) {
      idCondition = content.id.gt(idAfter);
    } else {
      idCondition = content.id.lt(idAfter);
    }
    BooleanExpression tieBreak = sortKey.eq(cursor).and(idCondition);
    return keyStep.or(tieBreak);
  }

  // 정렬 키와 id(tie-break)를 같은 방향으로 정렬하는 OrderSpecifier 배열
  private OrderSpecifier<?>[] cursorOrder(ContentSearchCondition condition) {
    Order order = condition.asc() ? Order.ASC : Order.DESC;
    return new OrderSpecifier<?>[] {
      new OrderSpecifier<>(order, sortKey(condition.sortBy())),
      new OrderSpecifier<>(order, content.id)
    };
  }

  // 정렬 키 표현식 (정렬에 사용) - Instant, Double, Long 모두 대응하기 위해서 따로 빼냄
  private ComparableExpression<?> sortKey(SortBy sortBy) {
    return switch (sortBy) {
      case CREATED_AT -> content.createdAt;
      case RATE -> Expressions.asComparable(content.averageRating);
      case WATCHER_COUNT -> Expressions.asComparable(watcherCount());
    };
  }

  // 활성 시청 세션 상관 서브쿼리 집계값
  // TODO: content를 참조하는 상관 서브쿼리라 콘텐츠 N건마다 watching_session 집계가 재실행됨.
  //  특히 sortBy=WATCHER_COUNT면 WHERE(keyStep, tieBreak)와 ORDER BY 세 곳에 서브쿼리가 들어가 비용이 커짐.
  //  추후 반정규화나 Redis ZSet 등으로 watcherCount를 사전 집계/캐싱해, 읽을 때마다 count 하는 구조를 개선.
  private Expression<Long> watcherCount() {
    return JPAExpressions.select(watchingSession.count())
        .from(watchingSession)
        .where(
            watchingSession.content.eq(content), // 상관 조건
            watchingSession.exitedAt.isNull(), // 활성 세션
            watchingSession.deletedAt.isNull()); // 논리삭제 제외
  }
}
