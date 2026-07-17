// 알림 목록 조회 부하테스트 시나리오.
//
// 실행:
//   k6 run -e BASE_URL=http://localhost:8080 -e CONFIG=smoke load-test/scenarios/notification-read.js
import {sleep} from 'k6';
import {BASE_URL, fetchCursorPages} from '../lib/http.js';
import {login} from '../lib/auth.js';
import {pickUser} from '../data/users.js';
import {optionsWith} from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:notifications-list}': ['p(95)<500'],
});

export function setup() {
  // 테스트 시작 전에 한 번 로그인하고, 모든 VU가 accessToken을 공유한다.
  const user = pickUser(0);
  const {accessToken} = login(user.email, user.password);
  return {accessToken};
}

export default function (data) {
  // 알림 목록은 CursorResponse 형식이므로 공통 커서 조회 helper를 사용한다.
  // MAX_PAGES 기본값만큼 cursor/idAfter를 따라가며 여러 페이지를 조회한다.
  fetchCursorPages(
      `${BASE_URL}/api/notifications?sortBy=createdAt&sortDirection=DESCENDING&limit=20`,
      data.accessToken,
      'notifications-list'
  );

  sleep(1);
}