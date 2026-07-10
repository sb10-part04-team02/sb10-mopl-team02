package com.team02.mopl.domain.content.ingestion.sportsdb.dto;

import java.util.List;

// SportsDB 이벤트 목록 API의 공통 응답
// https://www.thesportsdb.com/documentation
public record SportsDbEventsResponse(List<SportsDbEventDto> events) {

  // 결과가 없으면 {"events": null}을 반환하므로 null을 빈 리스트로 방어
  public SportsDbEventsResponse {
    events = events == null ? List.of() : List.copyOf(events);
  }
}
