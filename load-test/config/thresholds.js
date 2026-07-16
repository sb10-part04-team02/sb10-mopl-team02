// SLO threshold
// duration SLO 는 각 시나리오가 자기 태그로만 선언한다.
// 시나리오별 태그 SLO는 각 시나리오가 config/index.js 의 optionsWith({...}) 로 자기 태그를 선언한다.
export const thresholds = {
  http_req_failed: ['rate<0.01'], // 에러율 1% 미만
  checks: ['rate>0.99'], // 응답 검증 통과율 99% 이상

  // 로그인은 BCrypt + Redis 왕복이 있어 조회보다 느리다 → 태그로 분리해 느슨하게.
  'http_req_duration{name:sign-in}': ['p(95)<1500'],
};
