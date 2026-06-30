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

/*
- 동적 검색 조건 조합 (타입/키워드/태그)
- 복합키(sortKey, id) 기반 커서 페이지네이션 (정렬 키가 같을 때 id 로 tie-break)
- 정렬 키로 RATE / CREATED_AT / WATCHER_COUNT (인기순, 최신순, 평점순) 지원
 */
@RequiredArgsConstructor
public class ContentRepositoryImpl implements ContentRepositoryCustom {

  // Q 클래스 - Querydsl이 컴파일 시점에 엔티티 클래스를 기반으로 자동으로 생성해 주는 쿼리 전용 클래스
  private static final QContent content = QContent.content;
  private static final QTag tag = QTag.tag;
  private static final QWatchingSession watchingSession = QWatchingSession.watchingSession;

  private final JPAQueryFactory queryFactory; // Querydsl 라이브러리의 핵심 클래스

  @Override
  public List<Content> search(ContentSearchCondition condition) {
    return queryFactory
        .selectFrom(content) // SELECT content.* FROM content
        .where(
            content.deletedAt.isNull(), // 논리 삭제되지 않은 컨텐츠만
            contentTypeEq(condition.type()), // 콘텐츠 타입 일치
            keywordContains(condition.keyword()), // 제목/설명 키워드 매칭
            tagsExists(condition.tags()), // 특정 태그 보유 여부
            cursorAfter(condition)) // 커서 페이지네이션 경계 조건
        .orderBy(cursorOrder(condition)) // 정렬 키와 id를 같은 방향으로 정렬
        .limit(condition.limit()) // 한 페이지에서 가져올 최대 행 수 (페이지 크기)
        .fetch(); // 쿼리를 실제로 실행하여 결과를 List<Content>로 반환
  }

  // totalCount 산출용 — search()와 동일한 필터 적용하지만, 전체 건수만 집계
  @Override
  public long countBySearch(ContentSearchCondition condition) {
    Long count =
        queryFactory
            .select(content.count()) // SELECT COUNT(content.id)
            .from(content)
            .where(
                content.deletedAt.isNull(),
                contentTypeEq(condition.type()),
                keywordContains(condition.keyword()),
                tagsExists(condition.tags()))
            .fetchOne(); // 단일 값(개수) 조회
    return count == null ? 0L : count; // NPE 방지
  }

  /// === 동적 조건 메서드 - null이면 where에서 무시됨 ===

  // 콘텐츠 타입 일치 조건
  private BooleanExpression contentTypeEq(ContentType type) {
    return type == null ? null : content.contentType.eq(type);
  }

  // 키워드 부분 매칭 조건 - 제목/설명 대소문자 구분 없이 부분 매칭
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
    Comparable<?> cursor = condition.cursor(); // 직전 페이지 마지막 행의 정렬키 값
    // 커서의 실제 타입이 정렬 기준(sortBy)에 따라 다르기 때문에 Comparable<?> 사용
    if (cursor == null) {
      return null;
    }
    boolean asc = condition.asc(); // 정렬 방향
    UUID idAfter = condition.idAfter(); // 직전 페이지 마지막 행의 id (tie-break 기준)
    return switch (condition.sortBy()) {
        // 최신순
      case CREATED_AT -> after(content.createdAt, (Instant) cursor, idAfter, asc);
        // 평점순
      case RATE ->
          after(Expressions.asComparable(content.averageRating), (Double) cursor, idAfter, asc);
        // 인기순
      case WATCHER_COUNT ->
          after(Expressions.asComparable(watcherCount()), (Long) cursor, idAfter, asc);
    };
  }

  /*
  예를 들어 createdAt 내림차순(최신순)으로 페이징할 때:

  1페이지 마지막 행: createdAt = 2024-06-01, id = UUID-A
  2페이지 조건:
    (createdAt < 2024-06-01)                  -- keyStep: 정렬키가 더 작은 것
    OR
    (createdAt = 2024-06-01 AND id < UUID-A)  -- tieBreak: 같은 날짜면 id로 순서 결정
  */
  private static <T extends Comparable<T>> BooleanExpression after(
      ComparableExpression<T> sortKey, // DB 컬럼 표현식 최신순, 평점순, 인기순 받음
      T cursor, // 직전 페이지 마지막 행의 정렬키 값
      UUID idAfter, // 직전 페이지 마지막 행의 id
      boolean asc // 정렬 방향
      ) {
    // 오름차순 - gt / 내림차순 - lt
    BooleanExpression keyStep = asc ? sortKey.gt(cursor) : sortKey.lt(cursor);
    // tie-break 처리
    BooleanExpression idCondition;
    if (asc) {
      idCondition = content.id.gt(idAfter);
    } else {
      idCondition = content.id.lt(idAfter);
    }
    BooleanExpression tieBreak = sortKey.eq(cursor).and(idCondition);
    return keyStep.or(tieBreak);
  }

  // 정렬 키와 id(tie-break)를 같은 방향으로 정렬하는 OrderSpecifier 배열.
  private OrderSpecifier<?>[] cursorOrder(ContentSearchCondition condition) {
    Order order = condition.asc() ? Order.ASC : Order.DESC;
    return new OrderSpecifier<?>[] { // OrderSpecifier: 어떤 컬럼을, 어떤 방향으로 정렬할지 담는 객체
      new OrderSpecifier<>(order, sortKey(condition.sortBy())),
      new OrderSpecifier<>(order, content.id)
    };
  }

  // 정렬 키 표현식 (정렬에 사용) - Instant, Double, Long 모두 대응하기 위해서 따로 빼냄
  // ComparableExpression: QueryDSL에서 gt(), lt(), eq() 같은 비교 연산이 가능한 표현식 타입
  // Expressions.asComparable(): ComparableExpression으로 래핑하는 메서드 (타입 캐스팅 유팅)
  private ComparableExpression<?> sortKey(SortBy sortBy) {
    return switch (sortBy) {
      case CREATED_AT -> content.createdAt;
      case RATE -> Expressions.asComparable(content.averageRating);
      case WATCHER_COUNT -> Expressions.asComparable(watcherCount());
    };
  }

  // 활성 시청 세션 상관 서브쿼리 집계값
  private Expression<Long> watcherCount() { // SQL 표현식을 반환
    return JPAExpressions.select(watchingSession.count())
        .from(watchingSession)
        .where(
            watchingSession.content.eq(content), // 상관 조건
            watchingSession.exitedAt.isNull(), // 활성 세션
            watchingSession.deletedAt.isNull()); // 논리삭제 제외
  }
}
