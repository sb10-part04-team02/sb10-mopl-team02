package com.team02.mopl.domain.content.ingestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.team02.mopl.domain.content.enums.ContentSource;
import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.ingestion.ContentFetcher;
import com.team02.mopl.domain.content.ingestion.ExternalContentData;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentFetcherItemReaderTest {

  @Mock private ContentFetcher fetcher;

  private ContentFetcherItemReader reader;

  @BeforeEach
  void setUp() {
    reader = new ContentFetcherItemReader(fetcher);
  }

  @Test
  @DisplayName("read는 첫 호출에서만 fetch하고 항목을 순서대로 반환한 뒤 소진되면 null을 반환한다")
  void read_fetchesLazilyOnceAndIteratesInOrder() {
    // given
    ExternalContentData first = data("movie:1");
    ExternalContentData second = data("movie:2");
    given(fetcher.fetch()).willReturn(List.of(first, second));

    // when & then
    assertThat(reader.read()).isEqualTo(first);
    assertThat(reader.read()).isEqualTo(second);
    assertThat(reader.read()).isNull();
    assertThat(reader.read()).isNull(); // 소진 후 재호출에도 fetch가 다시 일어나지 않는다
    then(fetcher).should().fetch();
  }

  @Test
  @DisplayName("fetch 결과가 비어 있으면 즉시 null을 반환한다")
  void read_whenFetchReturnsEmpty_returnsNullImmediately() {
    // given
    given(fetcher.fetch()).willReturn(List.of());

    // when & then
    assertThat(reader.read()).isNull();
  }

  @Test
  @DisplayName("fetch 전체 실패는 ContentFetchException으로 전파되어 스텝을 실패시킨다 (noSkip 대상)")
  void read_whenFetchFails_throwsContentFetchException() {
    // given
    given(fetcher.fetch()).willThrow(new RuntimeException("외부 API 장애"));
    given(fetcher.source()).willReturn(ContentSource.TMDB);

    // when & then
    assertThatThrownBy(() -> reader.read())
        .isInstanceOf(ContentFetchException.class)
        .hasMessageContaining("TMDB");
  }

  private ExternalContentData data(String externalId) {
    return new ExternalContentData(
        ContentSource.TMDB, externalId, ContentType.MOVIE, "제목", "설명", "https://img", List.of());
  }
}
