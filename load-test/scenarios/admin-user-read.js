// ADMIN 유저 목록 조회 여정: GET /api/users(ADMIN 전용) 목록 커서 순회 + 필터 검색.
//
// 계정 전략이 다른 이유(다른 시나리오와의 예외)
//   유저 목록은 ADMIN 전용이고 ADMIN 계정은 1개다(InitAdmin 이 startup 에 1개 생성).
//   app.jwt.redis.max-account-count=1 이라 VU 마다 admin 으로 로그인하면 refresh 토큰이 서로 밀려난다.
//   그래서 setup 에서 1회만 로그인해 전체 VU 가 access 토큰을 공유한다.
//   access 토큰은 JWT 서명 검증이라 만료(10분) 전까지 유효하므로, 이 시나리오는 10분 이내 프로파일
//   (smoke 1m / load 4.5m / stress 7m)에서만 안전하다. refresh 는 쿠키가 setup 에 갇혀 불가능하다.
//
// 이 API 만 limit/sortDirection/sortBy 가 @NotNull 필수다(UserSearchRequest). 항상 셋 다 보낸다.
// 자격은 -e ADMIN_EMAIL / -e ADMIN_PASSWORD 로 주입한다(기본값 없음).
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... \
//         load-test/scenarios/admin-user-read.js
import { sleep, check } from 'k6';
import http from 'k6/http';
import { BASE_URL, authParams, checkCursorResponse, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { optionsWith } from '../config/index.js';

const USER_SORTS = ['name', 'email', 'createdAt'];

export const options = optionsWith({
  'http_req_duration{name:admin-users-list}': ['p(95)<800'],
  'http_req_duration{name:admin-users-search}': ['p(95)<800'],
});

export function setup() {
  const email = __ENV.ADMIN_EMAIL;
  const password = __ENV.ADMIN_PASSWORD;
  if (!email || !password) {
    throw new Error('ADMIN 자격이 없습니다. -e ADMIN_EMAIL=... -e ADMIN_PASSWORD=... 로 주입하세요.');
  }

  const { accessToken } = login(email, password);

  // ADMIN 권한 확인: 유저 목록이 403 이면 관리자 계정이 아니다.
  const probe = http.get(
    `${BASE_URL}/api/users?limit=1&sortBy=createdAt&sortDirection=DESCENDING`,
    authParams(accessToken)
  );
  if (probe.status === 403) {
    throw new Error('ADMIN 계정이 아닙니다(유저 목록 403). ADMIN_EMAIL/ADMIN_PASSWORD 를 확인하세요.');
  }
  if (probe.status !== 200) {
    throw new Error(`유저 목록 조회 실패(status ${probe.status}). 앱/자격을 확인하세요.`);
  }
  return { accessToken };
}

export default function (data) {
  const { accessToken } = data;
  const sortBy = USER_SORTS[__ITER % USER_SORTS.length];

  // 1) 유저 목록 커서 순회(정렬 회전)
  fetchCursorPages(
    `${BASE_URL}/api/users?sortBy=${sortBy}&sortDirection=DESCENDING&limit=20`,
    accessToken,
    'admin-users-list'
  );

  // 2) 이메일 필터 검색(시딩 계정 'bulk...@mopl.test' 매칭)
  const searchRes = http.get(
    `${BASE_URL}/api/users?emailLike=bulk&sortBy=email&sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: 'admin-users-search' } })
  );
  checkCursorResponse(searchRes, 'admin-users-search');
  check(searchRes, { 'admin-users-search: status 200': (r) => r.status === 200 });

  sleep(1);
}
