package com.team02.mopl.domain.playlist.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum PlaylistSortBy {
  UPDATED_AT("updatedAt"),
  SUBSCRIBE_COUNT("subscribeCount");

  @JsonValue // 드롭다운 메뉴 표시용
  private final String value;

  PlaylistSortBy(String value) {
    this.value = value;
  }
}
