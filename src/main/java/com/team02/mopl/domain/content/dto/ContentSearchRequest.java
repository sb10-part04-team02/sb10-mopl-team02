package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.global.enums.SortDirection;
import java.util.List;
import java.util.UUID;

public record ContentSearchRequest(
    ContentType typeEqual,
    String keywordLike,
    List<String> tagsIn,
    String cursor,
    UUID idAfter,
    int limit,
    SortDirection sortDirection,
    SortBy sortBy) {

  private static final int DEFAULT_LIMIT = 20;
  private static final int MAX_LIMIT = 100;

  // limit 미지정(0 이하)이면 기본값(20), 상한 초과면 MAX_LIMIT(100)로 보정한 페이지 크기
  public int normalizedLimit() {
    if (limit <= 0) {
      return DEFAULT_LIMIT;
    }
    return Math.min(limit, MAX_LIMIT);
  }

  // 다음 페이지 존재 여부(hasNext)를 판정하기 위해 한 건을 더 조회한다.
  // limit + 1 개가 조회되면 다음 페이지가 있다는 의미이며, 이 여분 1건으로 hasNext를 판정한다.
  public int fetchLimit() {
    return normalizedLimit() + 1;
  }
}
