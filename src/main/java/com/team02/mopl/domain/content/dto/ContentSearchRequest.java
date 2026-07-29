package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.List;
import java.util.UUID;

public record ContentSearchRequest(
    @Parameter(description = "콘텐츠 타입") ContentType typeEqual,
    @Parameter(description = "검색 키워드") String keywordLike,
    @Parameter(description = "태그 목록") List<String> tagsIn,
    @Parameter(description = "커서") String cursor,
    @Parameter(description = "보조 커서") UUID idAfter,
    @Parameter(description = "한 번에 가져올 개수", required = true) Integer limit,
    @Parameter(description = "정렬 방향", required = true) SortDirection sortDirection,
    @Parameter(description = "정렬 기준", required = true) SortBy sortBy)
    implements CursorPageRequest<SortBy> {}
