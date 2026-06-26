package com.team02.mopl.domain.review.dto;

// 콘텐츠별 리뷰 집계 결과 (리뷰 수, 평균 평점)
// API 응답/요청 DTO가 아닌, 리포지토리 집계 쿼리 결과를 서비스로 전달하기 위한 내부 전용 객체
// 리뷰가 없으면 averageRating이 null이므로 호출부에서 0.0 처리
public record ReviewAggregate(long reviewCount, Double averageRating) {}
