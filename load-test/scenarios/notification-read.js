// 알림 목록 조회 부하테스트 시나리오.
//
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke load-test/scenarios/notification-read.js
import {sleep} from 'k6';
import {BASE_URL, fetchCursorPages} from '../lib/http.js';
import {login} from '../lib/auth.js';
import {users} from '../data/users.js';
import {optionsWith} from '../config/index.js';

const SEEDED_NOTIFICATION_READ_EMAILS = new Set([
  'loadtest01@mopl.test',
  'loadtest02@mopl.test',
  'loadtest03@mopl.test',
]);

export const options = optionsWith({
  'http_req_duration{name:notifications-list}': ['p(95)<500'],
});

export function setup() {
  // 테스트 시작 전에 알림 seed 데이터가 들어간 계정만 로그인해 accessToken 목록을 만든다.
  // users.json에 다른 부하테스트 계정이 추가되어도 빈 알림 목록 조회로 지표가 왜곡되지 않게 한다.
  const seededUsers = users.filter(
      (user) => SEEDED_NOTIFICATION_READ_EMAILS.has(user.email));

  const tokens = seededUsers.map((user) => {
    const {accessToken} = login(user.email, user.password);
    return accessToken;
  });

  if (tokens.length === 0) {
    throw new Error(
        '알림 조회 seed 대상 계정을 찾지 못했습니다. load-test/data/users.json을 확인하세요.'
    );
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