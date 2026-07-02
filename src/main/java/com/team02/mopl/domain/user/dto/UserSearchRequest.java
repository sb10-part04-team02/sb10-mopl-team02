package com.team02.mopl.domain.user.dto;

import com.team02.mopl.domain.user.entity.enums.Role;
import com.team02.mopl.domain.user.enums.UserSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UserSearchRequest(
    @Parameter(description = "이메일") String emailLike,
    @Parameter(description = "권한") Role roleEqual,
    @Parameter(description = "계정 잠금 상태") Boolean isLocked,
    @Parameter(description = "커서") String cursor,
    @Parameter(description = "보조 커서") UUID idAfter,
    @Parameter(description = "한 번에 가져올 개수(1~100)", required = true, example = "20") @NotNull
        Integer limit,
    @Parameter(description = "정렬 방향", required = true) @NotNull SortDirection sortDirection,
    @Parameter(description = "정렬 기준", required = true) @NotNull UserSortBy sortBy)
    implements CursorPageRequest<UserSortBy> {}
