// 플레이리스트 조회 여정: 목록(커서 순회) -> 내 구독 목록 -> 키워드 검색 -> 상세.
// 응답에 요청자 기준 subscribedByMe 가 포함되므로 계정을 VU 별로 분산해야 값이 유의미해진다.
// 실행: k6 run -e BASE_URL=... -e CONFIG=smoke load-test/scenarios/playlist-read.js
import http from 'k6/http';
import { sleep, check } from 'k6';
import { BASE_URL, authParams, checkCursorResponse, fetchCursorPages } from '../lib/http.js';
import { login } from '../lib/auth.js';
import { getSession, assertAccountsCoverVus, seedEmail, ACCOUNT_OFFSET, SEED_PASSWORD } from '../lib/accounts.js';
import { optionsWith } from '../config/index.js';

const PLAYLIST_SORTS = ['updatedAt', 'subscribeCount'];

export const options = optionsWith({
  'http_req_duration{name:playlists-list}': ['p(95)<500'],
  'http_req_duration{name:playlists-subscribed}': ['p(95)<500'],
  'http_req_duration{name:playlists-search}': ['p(95)<800'],
  'http_req_duration{name:playlists-detail}': ['p(95)<300'],
});

export function setup() {
  assertAccountsCoverVus();
  const { accessToken } = login(seedEmail(ACCOUNT_OFFSET), SEED_PASSWORD);

  const res = http.get(`${BASE_URL}/api/playlists?limit=1`, authParams(accessToken));
  let hasPlaylist = false;
  try {
    hasPlaylist = (res.json('data') || []).length > 0;
  } catch (_) {
    hasPlaylist = false;
  }
  if (!hasPlaylist) {
    throw new Error('플레이리스트가 없습니다. seed/bulk-seed-social.sql 로 소셜 데이터를 먼저 시딩하세요.');
  }
  return {};
}

export default function () {
  const { accessToken, userId } = getSession();
  const sortBy = PLAYLIST_SORTS[__ITER % PLAYLIST_SORTS.length];

  // 1) 목록 커서 순회 (정렬 회전)
  const items = fetchCursorPages(
    `${BASE_URL}/api/playlists?sortBy=${sortBy}&sortDirection=DESCENDING&limit=20`,
    accessToken,
    'playlists-list'
  );
  // 계약: 응답 항목에 요청자 기준 subscribedByMe(boolean)가 포함돼야 한다.
  check(items, {
    'playlists-list: has subscribedByMe': (data) =>
      data.length === 0 || typeof data[0].subscribedByMe === 'boolean',
  });

  // 2) 내가 구독한 플레이리스트(subscriberIdEqual 조인 경로). 계정 분산 덕에 유저마다 결과가 다르다.
  const subRes = http.get(
    `${BASE_URL}/api/playlists?subscriberIdEqual=${userId}&sortBy=updatedAt&sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: 'playlists-subscribed' } })
  );
  checkCursorResponse(subRes, 'playlists-subscribed');

  // 3) 키워드 검색(시딩 title 'loadtest playlist N' 매칭)
  const searchRes = http.get(
    `${BASE_URL}/api/playlists?keywordLike=loadtest&sortBy=updatedAt&sortDirection=DESCENDING&limit=20`,
    authParams(accessToken, { tags: { name: 'playlists-search' } })
  );
  checkCursorResponse(searchRes, 'playlists-search');

  // 4) 랜덤 상세 조회
  if (items.length > 0) {
    const id = items[Math.floor(Math.random() * items.length)].id;
    const detailRes = http.get(
      `${BASE_URL}/api/playlists/${id}`,
      authParams(accessToken, { tags: { name: 'playlists-detail' } })
    );
    check(detailRes, { 'playlists-detail: status 200': (r) => r.status === 200 });
  }

  sleep(1);
}
