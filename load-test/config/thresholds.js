// SLO threshold 초안 — 팀 확정 및 1차 측정 후 조정.
// 평균이 아니라 p95/p99 로 잡는다(평균은 꼬리 지연을 숨김).
//
// 여기에는 전 시나리오 공통 SLO 만 둔다. 시나리오별 태그 SLO(contents-detail, contents-search 등)는
// 각 시나리오가 config/index.js 의 optionsWith({...}) 로 자기 태그를 선언한다.
// 공통 파일에 전부 넣으면 그 태그 요청이 없는 시나리오에서도 threshold 가 출력돼 노이즈가 되고,
// 시나리오를 넣고 뺄 때 이 파일을 함께 고쳐야 해 모듈 독립성이 깨지기 때문이다.
export const thresholds = {
  http_req_failed: ['rate<0.01'], // 에러율 1% 미만
  http_req_duration: ['p(95)<500', 'p(99)<1000'], // 조회 p95 500ms / p99 1s
  checks: ['rate>0.99'], // 응답 검증 통과율 99% 이상

  // 로그인은 BCrypt + Redis 왕복이 있어 조회보다 느리다 → 태그로 분리해 느슨하게.
  'http_req_duration{name:sign-in}': ['p(95)<1500'],
};
