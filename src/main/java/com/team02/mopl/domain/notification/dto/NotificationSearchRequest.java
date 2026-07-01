package com.team02.mopl.domain.notification.dto;

import com.team02.mopl.domain.notification.enums.NotificationSortBy;
import com.team02.mopl.global.dto.CursorPageRequest;
import com.team02.mopl.global.enums.SortDirection;
import java.util.UUID;

public record NotificationSearchRequest(
    String cursor,
    UUID idAfter,
    Integer limit,
    SortDirection sortDirection,
    NotificationSortBy sortBy)
    implements CursorPageRequest<NotificationSortBy> {}
