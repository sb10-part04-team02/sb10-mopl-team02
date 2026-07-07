package com.team02.mopl.domain.content.ingestion;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import java.util.List;

// 외부 API 응답을 소스와 무관하게 정규화한 수집 데이터. 멱등 upsert의 유일한 입력 타입.
public record ExternalContentData(
    ContentSource source,
    String externalId,
    ContentType contentType,
    String title,
    String description,
    String thumbnailUrl,
    List<String> tags) {

  public ExternalContentData {
    tags = List.copyOf(tags); // 방어적 복사
  }
}
