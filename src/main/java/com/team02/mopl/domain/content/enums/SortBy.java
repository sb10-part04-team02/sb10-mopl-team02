package com.team02.mopl.domain.content.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SortBy {
  CREATED_AT("createdAt"),
  WATCHER_COUNT("watcherCount"),
  RATE("rate");

  @JsonValue private final String value;
}
