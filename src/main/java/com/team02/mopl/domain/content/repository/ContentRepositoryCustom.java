package com.team02.mopl.domain.content.repository;

import com.team02.mopl.domain.content.dto.ContentSearchCondition;
import com.team02.mopl.domain.content.entity.Content;
import java.util.List;

// QueryDSL 기반 콘텐츠 목록 조회 (동적 필터 + 동적 정렬 + 복합 커서)
public interface ContentRepositoryCustom {

  // 콘텐츠 목록 데이터를 실제로 가져오는 메서드
  //   - 동적 필터(타입, 키워드, 태그) 적용
  //   - 동적 정렬(SortBy + asc/desc) 적용
  //   - 복합 커서(cursor + idAfter) 기반 페이징 적용
  //   - limit + 1건 조회해서 다음 페이지 존재 여부 판단 (hasNext)
  List<Content> search(ContentSearchCondition condition);

  // 같은 필터 조건으로 전체 건수만 세는 메서드 - SELECT COUNT(*)에 해당하는 역할
  long countBySearch(ContentSearchCondition condition);
}
