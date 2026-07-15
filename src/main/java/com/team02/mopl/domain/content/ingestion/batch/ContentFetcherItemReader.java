package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import java.util.Iterator;
import org.springframework.batch.item.ItemReader;

// ContentFetcher를 Spring Batch ItemReader로 감싸는 어댑터
// - 첫 read()에서 fetch()를 지연 호출해 정규화된 목록을 받고 한 건씩 반환한다 (소진 시 null)
// - 페이지/리그 단위 실패 격리와 매핑은 fetcher 내부에 이미 있으므로 별도 Processor를 두지 않는다
// - 실행마다 상태가 초기화되도록 @StepScope 빈으로 생성한다 (ContentIngestionJobConfig)
public class ContentFetcherItemReader implements ItemReader<ExternalContentData> {

  private final ContentFetcher fetcher;
  private Iterator<ExternalContentData> iterator;

  public ContentFetcherItemReader(ContentFetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Override
  public ExternalContentData read() {
    if (iterator == null) {
      try {
        iterator = fetcher.fetch().iterator();
      } catch (Exception e) {
        throw new ContentFetchException(fetcher.source(), e); // noSkip 대상: 스텝 즉시 실패
      }
    }
    return iterator.hasNext() ? iterator.next() : null;
  }
}
