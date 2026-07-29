package com.team02.mopl.domain.watching.dto;

import com.team02.mopl.domain.watching.enums.WatchingSessionSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;

public record WatchingSessionSearchRequest(
    @Parameter(description = "시청자 이름") String watcherNameLike,
    @Parameter(description = "커서") String cursor,
    @Parameter(description = "보조 커서") UUID idAfter,
    @Parameter(description = "한 번에 가져올 개수", required = true) Integer limit,
    @Parameter(description = "정렬 방향", required = true) SortDirection sortDirection,
    @Parameter(description = "정렬 기준", required = true) WatchingSessionSortBy sortBy)
    implements CursorPageRequest<WatchingSessionSortBy> {}
