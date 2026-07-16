package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import java.util.Iterator;
import org.springframework.batch.item.ItemReader;

// ContentFetcher를 Spring Batch ItemReader로 감싸는 어댑터
// - 페이지/리그 단위 실패 격리와 매핑은 fetcher 내부에 이미 있으므로 별도 Processor를 두지 않는다
// - 실행마다 상태가 초기화되도록 @StepScope 빈으로 생성한다 (ContentIngestionJobConfig)
public class ContentFetcherItemReader implements ItemReader<ExternalContentData> {

  private final ContentFetcher fetcher;
  private Iterator<ExternalContentData> iterator;

  public ContentFetcherItemReader(ContentFetcher fetcher) {
    this.fetcher = fetcher;
  }

  // iterator에 다음 항목이 있으면 그걸 주고,
  // 없으면 null을 반환 -> read()가 nulL을 반환하면 -> 데이터 끝으로 해석해 Step의 읽기를 종료
  @Override
  public ExternalContentData read() {
    if (iterator == null) { // 아직 fetch 하지 않음
      try {
        iterator = fetcher.fetch().iterator();
      } catch (Exception e) {
        throw new ContentFetchException(fetcher.source(), e); // noSkip 대상: 스텝 즉시 실패
      }
    }
    return iterator.hasNext() ? iterator.next() : null;
  }
}
