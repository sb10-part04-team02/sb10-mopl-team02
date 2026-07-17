// 알림 목록 조회 부하테스트 시나리오.
//
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke load-test/scenarios/notification-read.js
import {sleep} from 'k6';
import {BASE_URL, fetchCursorPages} from '../lib/http.js';
import {login} from '../lib/auth.js';
import {users} from '../data/users.js';
import {optionsWith} from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:notifications-list}': ['p(95)<500'],
});

export function setup() {
  // 테스트 시작 전에 seed 계정들을 각각 로그인해 accessToken 목록을 만든다.
  // VU들이 서로 다른 사용자의 알림 목록을 조회하도록 분산해 단일 사용자 캐시 편향을 줄인다.
  const tokens = users.map((user) => {
    const {accessToken} = login(user.email, user.password);
    return accessToken;
  });

  if (tokens.length === 0) {
    throw new Error('load-test/data/users.json에 테스트 계정이 없습니다.');
  }

  return {tokens};
}

export default function (data) {
  // VU 번호를 기준으로 토큰을 고르게 선택한다.
  // __VU는 1부터 시작하므로 배열 index에 맞추기 위해 1을 뺀다.
  const token = data.tokens[(__VU - 1) % data.tokens.length];

  // 알림 목록은 CursorResponse 형식이므로 공통 커서 조회 helper를 사용한다.
  // MAX_PAGES 기본값만큼 cursor/idAfter를 따라가며 여러 페이지를 조회한다.
  fetchCursorPages(
      `${BASE_URL}/api/notifications?sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
      token,
      'notifications-list'
  );

  sleep(1);
}