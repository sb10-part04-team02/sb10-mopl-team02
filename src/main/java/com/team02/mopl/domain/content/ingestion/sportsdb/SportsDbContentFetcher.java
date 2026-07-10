package com.team02.mopl.domain.content.ingestion.sportsdb;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import com.team02.mopl.domain.content.ingestion.exception.ExternalApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// SportsDB 경기 수집기
// 설정된 리그+시즌 목록을 순회하며, 리그 단위 호출 실패는 warn 로그 후 나머지 리그를 계속 수집
@Slf4j
@Component
public class SportsDbContentFetcher implements ContentFetcher {

  private final SportsDbClient sportsDbClient;
  private final SportsDbProperties properties;
  private final String defaultThumbnailUrl;

  public SportsDbContentFetcher(
      SportsDbClient sportsDbClient,
      SportsDbProperties properties,
      @Value("${app.storage.default-thumbnail-url:}") String defaultThumbnailUrl) {
    this.sportsDbClient = sportsDbClient;
    this.properties = properties;
    this.defaultThumbnailUrl = defaultThumbnailUrl;
  }

  @Override
  public ContentSource source() {
    return ContentSource.SPORTS_DB;
  }

  @Override
  public List<ExternalContentData> fetch() {
    SportsDbEventMapper mapper = new SportsDbEventMapper(defaultThumbnailUrl);
    List<ExternalContentData> results = new ArrayList<>();
    for (SportsDbProperties.League league : properties.leagues()) {
      try {
        int before = results.size();
        sportsDbClient.fetchSeasonEvents(league.id(), league.season()).stream() // 응답 순회
            .map(mapper::map) // 원본 DTO를 Optional<ExternalContentData>로 변환
            .flatMap(Optional::stream) // 유효한 항목만 남김
            .forEach(results::add); // 결과 리스트에 추가
        if (results.size() == before) {
          // 시즌 표기 오기 등은 에러가 아니라 빈 결과({"events": null})로 오므로 로그로 가시화
          log.warn(
              "SportsDB 리그 수집 결과가 0건입니다. 시즌 표기를 확인하세요. leagueId={}, season={}",
              league.id(),
              league.season());
        }
      } catch (ExternalApiException e) {
        log.warn(
            "SportsDB 리그 수집 실패로 해당 리그를 건너뜁니다. leagueId={}, season={}",
            league.id(),
            league.season(),
            e);
      }
    }
    return results;
  }
}
