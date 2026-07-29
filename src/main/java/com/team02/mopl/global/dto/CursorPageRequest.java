package com.team02.mopl.global.dto;

import com.team02.mopl.global.enums.SortDirection;
import java.util.UUID;

// 커서 페이지네이션 공통 요청 계약. SortBy 는 도메인별 enum 으로 받는다.
// S는 정렬 기준을 나타내는 도메인별 enum
public interface CursorPageRequest<S extends Enum<S>> {

  int DEFAULT_LIMIT = 20;
  int MAX_LIMIT = 100;

  String cursor(); // 마지막으로 받은 항목의 정렬값

  UUID idAfter(); // 마지막으로 받은 항목의 UUID

  Integer limit(); // 한 번에 가져올 최대 항목 수

  SortDirection sortDirection(); // ASCENDING / DESCENDING

  S sortBy(); // 도메인별 정렬 기준 enum

  // 클라이언트가 limit을 안 보냈을 경우 기본값(20)으로 대체하고, 1 ~ MAX_LIMIT 범위로 조정
  static int normalizeLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    return Math.min(Math.max(limit, 1), MAX_LIMIT);
  }

  // sortDirection이 없으면 내림차순(DESCENDING)을 기본으로 사용
  static SortDirection normalizeSortDirection(SortDirection direction) {
    return direction == null ? SortDirection.DESCENDING : direction;
  }

  // cursor·idAfter는 항상 함께 와야 한다. 둘 다 있거나(다음 페이지) 둘 다 없어야(첫 페이지) 유효하다
  static boolean isValidCursorCombo(String cursor, UUID idAfter) {
    boolean hasCursor = cursor != null && !cursor.isBlank();
    boolean hasIdAfter = idAfter != null;
    return hasCursor == hasIdAfter;
  }
}
