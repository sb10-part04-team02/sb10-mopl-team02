package com.team02.mopl.domain.content.ingestion.sportsdb;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

// The Sports DB API 연동 설정
// - apiKey는 v1 인증 방식상 URL 경로 세그먼트로 들어간다 (헤더 인증 아님). 무료 테스트 키 "123" 사용 가능
// - leagues는 수집 대상 리그+시즌 목록. 시즌 표기는 리그마다 다르므로(축구 "2025-2026", 야구 "2025") 검증하지 않는다
@ConfigurationProperties(prefix = "app.sportsdb")
public record SportsDbProperties(
    String apiKey,
    String baseUrl,
    Duration connectTimeout,
    Duration readTimeout,
    List<League> leagues) {

  public SportsDbProperties {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("SportsDB api-key는 필수입니다.");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("SportsDB base-url은 필수입니다.");
    }
    if (connectTimeout == null || connectTimeout.isNegative() || connectTimeout.isZero()) {
      throw new IllegalStateException("SportsDB connect-timeout은 필수고 0보다 커야 합니다.");
    }
    if (readTimeout == null || readTimeout.isNegative() || readTimeout.isZero()) {
      throw new IllegalStateException("SportsDB read-timeout은 필수고 0보다 커야 합니다.");
    }
    // 리그 미설정은 부팅을 막지 않는다 (수집 시 0건)
    leagues = leagues == null ? List.of() : List.copyOf(leagues);
  }

  // 수집 대상 리그 한 건 (id: SportsDB 리그 id, season: 해당 리그의 시즌 표기)
  public record League(String id, String season) {

    public League {
      if (id == null || id.isBlank()) {
        throw new IllegalStateException("SportsDB league id는 필수입니다.");
      }
      if (season == null || season.isBlank()) {
        throw new IllegalStateException("SportsDB league season은 필수입니다.");
      }
    }
  }
}
