package com.team02.mopl.domain.content.ingestion.batch;

import com.team02.mopl.domain.content.enums.ContentSource;

// fetch 전체 실패(소스 수준 장애)를 항목 단위 실패와 구분하기 위한 예외
// 스텝의 noSkip 대상으로 등록되어 스텝을 즉시 실패시킨다
// (skip 대상이 되면 read 재호출 -> fetch 전체 재실행이 skipLimit만큼 반복되는 것을 방지)
public class ContentFetchException extends RuntimeException {

  public ContentFetchException(ContentSource source, Throwable cause) {
    super("콘텐츠 fetch에 실패했습니다. source=" + source, cause);
  }
}
