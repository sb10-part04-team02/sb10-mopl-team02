package com.team02.mopl.domain.content.ingestion.sportsdb.dto;

// SportsDB /eventsseason.php 응답의 경기 1건 (수집에 필요한 필드만 역직렬화)
// https://www.thesportsdb.com/documentation
public record SportsDbEventDto(
    String idEvent, // externalId 재료 (필수)
    String strEvent, // 경기명, 예: "Liverpool vs Bournemouth" (필수)
    String strDescriptionEN, // 설명. 대부분 비어 있어 리그/시즌/경기장으로 합성 대체
    String strSport, // 종목, 예: "Soccer" -> 태그
    String strLeague, // 리그명, 예: "English Premier League" -> 태그
    String strSeason, // 시즌 표기, 예: "2025-2026"
    String strVenue, // 경기장, 예: "Anfield"
    String dateEvent, // 경기 일자, 예: "2025-08-15"
    String strThumb, // 1순위 썸네일 URL
    String strPoster) {} // 2순위 썸네일 URL
