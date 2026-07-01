package com.team02.mopl.domain.content.dto;

import com.team02.mopl.domain.content.enums.ContentType;
import com.team02.mopl.domain.content.enums.SortBy;
import java.util.List;
import java.util.UUID;

// 콘텐츠 목록 조회용 정규화된 검색 조건 (서비스 -> 레포지토리 전달)
// cf. ContentSearchRequest <- Controller 에서 입력으로 받는 DTO
//  - keyword: 공백 정규화 후 null 또는 검색어
//  - tags: 공백/중복 제거된 활성 태그 이름. 비어있으면 태그 필터 미적용
//  - cursor: sortBy 타입에 맞게 변경된 정렬 키 값(Instant/Double/Long). null 이면 첫 페이지
public record ContentSearchCondition(
    ContentType type,
    String keyword,
    List<String> tags,
    SortBy sortBy,
    boolean asc,
    Comparable<?> cursor,
    UUID idAfter,
    int limit) {}
