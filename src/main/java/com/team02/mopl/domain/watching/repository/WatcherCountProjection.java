package com.team02.mopl.domain.watching.repository;

import java.util.UUID;

// 콘텐츠별 활성 시청자 수 일괄 집계 결과 프로젝션
// Spring이 JPQL의 AS alias와 getter 이름을 매핑해 런타임에 구현체를 생성한다.
public interface WatcherCountProjection {

  UUID getContentId();

  long getCount();
}
