package com.team02.mopl.domain.review.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ReviewSortBy {
  CREATED_AT("createdAt"),
  RATING("rating");

  @JsonValue // 드롭다운 메뉴 표시용
  private final String value;

  ReviewSortBy(String value) {
    this.value = value;
  }
}
