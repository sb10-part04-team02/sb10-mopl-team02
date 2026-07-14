// 알림 조회 여정: 인증 사용자의 알림 목록(커서 순회).
// 수신자 = principal 이라 계정을 VU 별로 분산해야 한 유저의 알림만 반복 조회하는 걸 피한다.
// 전제: seed/bulk-seed-social.sql 이 계정마다 알림(notifications_per_user=30)을 심어 둔다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/notification-read.js
import { sleep } from 'k6';
import http from 'k6/http';
import { BASE_URL, authParams, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { getSession, assertAccountsCoverVus, seedEmail, ACCOUNT_OFFSET, SEED_PASSWORD } from '../lib/accounts.js';
import { optionsWith } from '../config/index.js';

export const options = optionsWith({
  'http_req_duration{name:notifications-list}': ['p(95)<500'],
});

export function setup() {
  assertAccountsCoverVus();
  const { accessToken } = login(seedEmail(ACCOUNT_OFFSET), SEED_PASSWORD);

  const res = http.get(`${BASE_URL}/api/notifications?limit=1`, authParams(accessToken));
  let hasNotification = false;
  try {
    hasNotification = (res.json('data') || []).length > 0;
  } catch (_) {
    hasNotification = false;
  }
  if (!hasNotification) {
    throw new Error('알림이 없습니다. seed/bulk-seed-social.sql 로 소셜 데이터를 먼저 시딩하세요.');
  }
  return {};
}

export default function () {
  const { accessToken } = getSession();

  // 알림 sortBy 는 createdAt 단일값이라 생략하고 방향만 준다(서비스가 기본값을 채운다).
  fetchCursorPages(
    `${BASE_URL}/api/notifications?sortDirection=DESCENDING&limit=20`,
    accessToken,
    'notifications-list'
  );

  sleep(1);
}
