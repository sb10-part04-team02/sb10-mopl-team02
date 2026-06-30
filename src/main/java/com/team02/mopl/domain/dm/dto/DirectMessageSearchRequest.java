package com.team02.mopl.domain.dm.dto;

import com.team02.mopl.domain.dm.enums.DirectMessageSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record DirectMessageSearchRequest(
    @Pattern(
            regexp =
                "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d{1,9})?(?:Z|[+-]\\d{2}:\\d{2})$",
            message = "cursor는 ISO-8601 date-time 형식이어야 합니다")
        String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    DirectMessageSortBy sortBy)
    implements CursorPageRequest<DirectMessageSortBy> {

  @AssertTrue(message = "cursor와 idAfter는 함께 제공되거나 모두 생략되어야 합니다")
  public boolean isCursorPairValid() {
    return (cursor == null) == (idAfter == null);
  }
}
