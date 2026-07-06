package com.team02.mopl.domain.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum UserSortBy {
  NAME("name"),
  EMAIL("email"),
  CREATED_AT("createdAt"),
  IS_LOCKED("isLocked"),
  ROLE("role");

  @JsonValue // 드롭다운 메뉴 표시용
  private final String value;

  UserSortBy(String value) {
    this.value = value;
  }
}
