package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.enums.ContentSource;
import java.util.List;

/** 외부 소스별 콘텐츠 수집기. 소스가 추가되면 이 인터페이스의 구현체를 등록한다. */
public interface ContentFetcher {

  ContentSource source();

  /** 외부 API에서 콘텐츠를 조회해 정규화된 수집 데이터 목록으로 반환한다. */
  List<ExternalContentData> fetch();
}
