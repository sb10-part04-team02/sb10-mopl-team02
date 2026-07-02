package com.team02.mopl.domain.user.enums;

import lombok.Getter;

@Getter
public enum UserSortBy {
  NAME("name"),
  EMAIL("email"),
  CREATED_AT("createdAt"),
  IS_LOCKED("isLocked"),
  ROLE("role");

  private final String value;

  UserSortBy(String value) {
    this.value = value;
  }
}
