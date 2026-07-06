package com.team02.mopl.domain.watching.dto;

import com.team02.mopl.domain.watching.enums.WatchingSessionSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import java.util.UUID;

public record WatchingSessionSearchRequest(
    String watcherNameLike,
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    WatchingSessionSortBy sortBy)
    implements CursorPageRequest<WatchingSessionSortBy> {

  @AssertTrue(message = "cursor와 idAfter는 함께 제공되거나 모두 생략되어야 합니다")
  public boolean isCursorPairValid() {
    return (cursor == null) == (idAfter == null);
  }
}
