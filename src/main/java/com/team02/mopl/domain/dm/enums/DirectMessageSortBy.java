package com.team02.mopl.domain.dm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum DirectMessageSortBy {
  CREATED_AT("createdAt");

  @JsonValue // 응답 직렬화 시 camelCase 값 사용
  private final String value;

  DirectMessageSortBy(String value) {
    this.value = value;
  }
}
