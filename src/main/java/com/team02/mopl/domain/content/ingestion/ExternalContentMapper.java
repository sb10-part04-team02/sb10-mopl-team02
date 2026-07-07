package com.team02.mopl.domain.content.ingestion;

import java.util.Optional;

/**
 * 외부 API 원시 응답 1건을 정규화된 수집 데이터로 변환한다.
 *
 * <p>계약: 필수 값(외부 식별자, 제목 등)이 누락된 응답은 예외 대신 {@link Optional#empty()}를 반환하고 warn 로그를 남긴다. 불량 데이터 1건이
 * 수집 전체를 중단시키지 않기 위함이다.
 */
public interface ExternalContentMapper<T> {

  Optional<ExternalContentData> map(T raw);
}
