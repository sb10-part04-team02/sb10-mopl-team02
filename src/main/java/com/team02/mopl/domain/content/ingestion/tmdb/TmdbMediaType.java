package com.team02.mopl.domain.content.ingestion.tmdb;

// TMDB discover 백필 대상 매체 구분. discover 경로와 개봉일 필드명을 함께 보유한다.
// - movie는 primary_release_date, tv는 first_air_date로 정렬/상한 필드명이 다르다
// - sort_by=<field>.desc, <field>.lte=<커서> 조립에 그대로 쓰인다
public enum TmdbMediaType {
  MOVIE("/discover/movie", "primary_release_date"),
  TV("/discover/tv", "first_air_date");

  private final String discoverPath;
  private final String dateField;

  TmdbMediaType(String discoverPath, String dateField) {
    this.discoverPath = discoverPath;
    this.dateField = dateField;
  }

  public String discoverPath() {
    return discoverPath;
  }

  public String dateField() {
    return dateField;
  }
}
