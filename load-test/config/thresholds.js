// SLO threshold 초안 — 팀 확정 및 1차 측정 후 조정.
// 평균이 아니라 p95/p99 로 잡는다(평균은 꼬리 지연을 숨김).
export const thresholds = {
  http_req_failed: ['rate<0.01'], // 에러율 1% 미만
  http_req_duration: ['p(95)<500', 'p(99)<1000'], // 조회 p95 500ms / p99 1s
  checks: ['rate>0.99'], // 응답 검증 통과율 99% 이상

  // 로그인은 BCrypt + Redis 왕복이 있어 조회보다 느리다 → 태그로 분리해 느슨하게.
  'http_req_duration{name:sign-in}': ['p(95)<1500'],
};
